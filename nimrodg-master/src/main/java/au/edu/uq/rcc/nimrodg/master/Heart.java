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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Heart, manages heartbeats, expiry timeouts, etc.
 */
class Heart implements ConfigListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(Heart.class);
	/* Time in seconds between shutdown attempts. */
	public static final long DEFAULT_EXPIRY_RETRY_INTERVAL = 5;
	/* Maximum number of retries before force disconnecting. */
	public static final long DEFAULT_EXPIRY_RETRY_COUNT = 5;
	/* Time in seconds between heartbeats. */
	public static final long DEFAULT_HEARTBEAT_INTERVAL = 5;
	/* Number of missed heartbeats before an agent is expired. */
	public static final long DEFAULT_HEARTBEAT_MISSED_THRESHOLD = 3;

	private static class ExpiryInfo {

		public Instant lastExpiryCheck;
		public int retryCount;

		public Instant lastPing;
		public long missedBeats;

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

		void disconnectAgent(UUID u, AgentInfo.ShutdownReason reason, int signal);

		void pingAgent(UUID u);

		Instant getLastHeardFrom(UUID u);

		Instant getWalltime(UUID u);

		void logInfo(String fmt, Object... args);

		void logTrace(String fmt, Object... args);
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
		expiryInfo.keySet().forEach(u -> tickAgent(u, now));
	}

	private void tickAgent(UUID u, Instant now) {
		ExpiryInfo ei = expiryInfo.get(u);

		tickWalltime(u, ei, now);

		if(!ei.isExpiring()) {
			tickHeartbeat(u, ei, now);
		}

	}

	private void tickWalltime(UUID u, ExpiryInfo ei, Instant now) {
		if(now.isBefore(ops.getWalltime(u))) {
			return;
		}

		long timeSinceExpiryCheck = ei.lastExpiryCheck.until(now, ChronoUnit.SECONDS);
		if(timeSinceExpiryCheck < expiryRetryInterval || expiryRetryInterval <= 0) {
			return;
		}

		if(ei.retryCount >= expiryRetryCount) {
			ops.logInfo("Agent %s ignored %d termination requests, marking as disconnected...", u, ei.retryCount);
			ops.disconnectAgent(u, AgentInfo.ShutdownReason.Requested, -1);
			ei.lastExpiryCheck = now;
			return;
		}

		if(ei.retryCount == 0) {
			ops.logInfo("Agent %s hit walltime, attempting to terminate...", u);
		}
		ops.terminateAgent(u);
		++ei.retryCount;
		ei.lastExpiryCheck = now;
	}

	private void tickHeartbeat(UUID u, ExpiryInfo ei, Instant now) {
		/* FIXME: This logic needs to be redone. */
		long commDiff = ops.getLastHeardFrom(u).until(now, ChronoUnit.SECONDS);
		/* FIXME: Set this to the actual delay */
		long processingDelay = 1;
		if(commDiff < (heartbeatInterval + processingDelay) || heartbeatInterval == 0) {
			return;
		}

		if(ei.missedBeats >= heartbeatMissedThreshold && heartbeatMissedThreshold > 0) {
			ops.logInfo("Agent %s missed %d heartbeats, expiring...", u, ei.missedBeats);
			ops.expireAgent(u);
			return;
		}

		if(Duration.between(ei.lastPing, now).getSeconds() > heartbeatInterval) {
			ops.pingAgent(u);
			ei.lastPing = now;
			++ei.missedBeats;
			if(ei.missedBeats > 1) {
				ops.logTrace("Agent %s now at %d missed beats.", u, ei.missedBeats);
			}
		}
	}

	public void onAgentPong(UUID u) {
		/* We've received a heartbeat, reset */
		ExpiryInfo ei = expiryInfo.get(u);
		if(ei == null) {
			/* Will happen if we've already been expired. Tough luck. */
			return;
		}
		ei.missedBeats = 0;
	}

	/**
	 * Called when an agent is created.
	 *
	 * This may not use any operations.
	 *
	 * @param u The UUID of the agent.
	 */
	public void onAgentCreate(UUID u, Instant now) {
		expiryInfo.put(u, new ExpiryInfo(now));
	}

	/**
	 * Reset the ping timer, causing a ping to be sent next tick.
	 *
	 * @param u The UUID of the agent.
	 */
	public void resetPingTimer(UUID u) {
		ExpiryInfo ei = expiryInfo.get(u);
		if(ei != null) {
			ei.lastPing = Instant.MIN;
		}
	}

	/**
	 * Reset the heartbeat count.
	 *
	 * @param u The UUID of the agent.
	 */
	public void resetBeats(UUID u) {
		ExpiryInfo ei = expiryInfo.get(u);
		if(ei != null) {
			ei.missedBeats = 0;
		}
	}

	/**
	 * Force-expire an agent.
	 *
	 * @param u The UUID of the agent.
	 */
	public void forceExpire(UUID u) {
		ExpiryInfo ei = expiryInfo.get(u);
		if(ei != null) {
			ei.missedBeats = Integer.MAX_VALUE;
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
		if(ei == null) {
			/* Sometimes happens if we've been killed before a expiry has gone through. */
			return;
		}

		/* Only log if we've tried to terminate. */
		if(ei.isExpiring()) {
			ops.logInfo("Agent %s expired on attempt %d", u, ei.retryCount);
		}
	}

	@Override
	public void onConfigChange(String key, String oldValue, String newValue) {
		Objects.requireNonNull(key, "key");

		switch(key) {
			case "nimrod.master.heart.expiry_retry_interval": {
				expiryRetryInterval = ConfigListener.get(newValue, expiryRetryInterval, DEFAULT_EXPIRY_RETRY_INTERVAL, 0, Long.MAX_VALUE);
				break;
			}
			case "nimrod.master.heart.expiry_retry_count": {
				expiryRetryCount = ConfigListener.get(newValue, expiryRetryCount, DEFAULT_EXPIRY_RETRY_COUNT);
				break;
			}
			case "nimrod.master.heart.interval": {
				heartbeatInterval = ConfigListener.get(newValue, heartbeatInterval, DEFAULT_HEARTBEAT_INTERVAL);
				expiryInfo.values().forEach(v -> v.retryCount = 0);
				break;
			}
			case "nimrod.master.heart.missed_threshold": {
				heartbeatMissedThreshold = ConfigListener.get(newValue, heartbeatMissedThreshold, DEFAULT_HEARTBEAT_MISSED_THRESHOLD);
				break;
			}
		}
	}
}
