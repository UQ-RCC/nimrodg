/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Heart, manages heartbeats, expiry timeouts, etc.
 */
class Heart {

	private static final Logger LOGGER = LogManager.getLogger(Heart.class);

	/* Time in seconds between shutdown attempts. */
	private static final long DEFAULT_EXPIRY_RETRY_INTERVAL = 5;
	/* Maximum number of retries before force disconnecting. */
	private static final long DEFAULT_EXPIRY_RETRY_COUNT = 5;
	/* Time in seconds between heartbeats. */
	private static final long DEFAULT_HEARTBEAT_INTERVAL = 5;
	/* Number of missed heartbeats before an agent is expired. */
	private static final long DEFAULT_HEARTBEAT_MISSED_THRESHOLD = 3;

	private static class ExpiryInfo {

		public Instant lastExpiryCheck;
		public int retryCount;

		public Instant lastPing;
		public int missedBeats;

		public ExpiryInfo(Instant now) {
			this.lastExpiryCheck = Instant.MIN;
			this.retryCount = 0;
			this.lastPing = now;
			this.missedBeats = 0;
		}

		/* This is more for readability purposes than anything else. */
		public boolean isExpiring() {
			return this.retryCount > 0;
		}

	}

	private final Operations ops;
	private final Map<UUID, ExpiryInfo> expiryInfo;
	private long expiryRetryInterval;
	private long expiryRetryCount;
	private long heartbeatInterval;
	private long heartbeatMissedThreshold;

	public interface Operations {

		void expireAgent(UUID u);

		void terminateAgent(UUID u);

		void disconnectAgent(UUID u, AgentShutdown.Reason reason, int signal);

		void pingAgent(UUID u);

		Instant getLastHeardFrom(UUID u);

		Instant getExpiryTime(UUID u);
	}

	Heart(Operations ops) {
		this.ops = ops;
		this.expiryInfo = new HashMap<>();
		this.expiryRetryInterval = DEFAULT_EXPIRY_RETRY_INTERVAL;
		this.expiryRetryCount = DEFAULT_EXPIRY_RETRY_COUNT;
		this.heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
		this.heartbeatMissedThreshold = DEFAULT_HEARTBEAT_MISSED_THRESHOLD;
	}

	public void tick(Instant now) {
		Set<UUID> exps = new HashSet<>();
		expiryInfo.keySet().forEach(u -> tickAgent(u, now, exps));
		exps.forEach(u -> expiryInfo.remove(u));
	}

	private void tickAgent(UUID u, Instant now, Set<UUID> exps) {
		ExpiryInfo ei = expiryInfo.get(u);

		tickExpiry(u, ei, now);

		if(!ei.isExpiring()) {
			tickHeartbeat(u, ei, now, exps);
		}

	}

	private void tickExpiry(UUID u, ExpiryInfo ei, Instant now) {
		if(now.isBefore(ops.getExpiryTime(u))) {
			return;
		}

		long timeSinceExpiryCheck = ei.lastExpiryCheck.until(now, ChronoUnit.SECONDS);
		if(timeSinceExpiryCheck < expiryRetryInterval || expiryRetryInterval <= 0) {
			return;
		}

		if(ei.retryCount >= expiryRetryCount) {
			LOGGER.info("Agent {} ignored {} expiry requests, marking as disconnected...", u, ei.retryCount);
			ops.disconnectAgent(u, AgentShutdown.Reason.Requested, -1);
			ei.lastExpiryCheck = now;
			return;
		}

		ops.terminateAgent(u);
		++ei.retryCount;
		ei.lastExpiryCheck = now;
	}

	private void tickHeartbeat(UUID u, ExpiryInfo ei, Instant now, Set<UUID> exps) {
		//long commDiff = ops.getLastHeardFrom(u).until(now.plus(1, ChronoUnit.SECONDS), ChronoUnit.SECONDS);
		long commDiff = ops.getLastHeardFrom(u).until(now, ChronoUnit.SECONDS);
		//LOGGER.trace("Agent {} commDiff = {}", u, commDiff);
		/* FIXME: Set this to the actual delay */
		long processingDelay = 1;
		if(commDiff < (heartbeatInterval + processingDelay) || heartbeatInterval == 0) {
			return;
		}

		if(ei.missedBeats >= heartbeatMissedThreshold && heartbeatMissedThreshold > 0) {
			LOGGER.info("Agent {} missed {} heartbeats, expiring...", u, ei.missedBeats);
			exps.add(u);
			ops.expireAgent(u);
			return;
		}

		if(Duration.between(ei.lastPing, now).getSeconds() > heartbeatInterval) {
			ops.pingAgent(u);
			ei.lastPing = now;
			++ei.missedBeats;
			//LOGGER.trace("Agent {} now at {} missed beats.", u, ei.missedBeats);
		}
	}

	public void onAgentPong(UUID u) {
		/* We've received a heartbeat, reset */
		ExpiryInfo ei = expiryInfo.get(u);
		ei.missedBeats = 0;
	}

	/**
	 * Called when an agent connects.
	 *
	 * This may not use any operations.
	 *
	 * @param u The UUID of the agent.
	 */
	public void onAgentConnect(UUID u, Instant now) {
		expiryInfo.put(u, new ExpiryInfo(now));
	}

	/**
	 * Reset the ping timer, causing a ping to be sent next tick.
	 * @param u The UUID of the agent.
	 */
	public void resetPingTimer(UUID u) {
		ExpiryInfo ei = expiryInfo.get(u);
		if(ei != null) {
			ei.lastPing = Instant.MIN;
		}
	}

	/**
	 * Called when an agent disconnects.
	 *
	 * This may not use any operations.
	 *
	 * @param u The UUID of the agent.
	 */
	public void onAgentDisconnect(UUID u) {
		ExpiryInfo ei = expiryInfo.remove(u);
		/* Only log if we've tried to terminate. */
		if(ei.isExpiring()) {
			LOGGER.info("Agent {} expired on attempt {}", u, ei.retryCount);
		}
	}

	public void onConfigChange(String key, String oldValue, String newValue) {
		switch(key) {
			case "nimrod.master.heart.expiry_retry_interval": {
				expiryRetryInterval = NimrodUtils.parseOrDefaultUnsigned(newValue, expiryRetryInterval);
				break;
			}
			case "nimrod.master.heart.expiry_retry_count": {
				expiryRetryCount = NimrodUtils.parseOrDefault(newValue, expiryRetryCount);
				break;
			}
			case "nimrod.master.heart.interval": {
				heartbeatInterval = NimrodUtils.parseOrDefault(newValue, heartbeatInterval);
				expiryInfo.values().forEach(v -> v.retryCount = 0);
				break;
			}
			case "nimrod.master.heart.missed_threshold": {
				heartbeatMissedThreshold = NimrodUtils.parseOrDefault(newValue, heartbeatMissedThreshold);
				break;
			}
		}
	}
}
