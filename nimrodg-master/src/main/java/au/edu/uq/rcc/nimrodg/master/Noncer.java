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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Noncer, because I'm bad with names.
 *
 * Keeps a rolling list of seen nonces until the valid time period has elapsed.
 * There should be one instance of this per agent.
 *
 * NB: Even though a nonce is a long, all values are compared as if they're unsigned.
 */
public class Noncer implements ConfigListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(Noncer.class);

	public static final long DEFAULT_DURATION = 5;
	public static final long DEFAULT_MAX_PING_MS = 1000;

	private final TreeMap<Long, Instant> knownNonces;
	private final Set<Long> toRemove;

	/**
	 * The minimum allowed nonce.
	 */
	private long minimumNonce;
	private Duration duration;
	private long ping;
	private long maxPing;

	public Noncer() {
		this.knownNonces = new TreeMap<>();
		this.toRemove = new HashSet<>();
		this.duration = Duration.ofSeconds(DEFAULT_DURATION);
		this.ping = 0;
		this.maxPing = DEFAULT_MAX_PING_MS;
	}

	public void setDuration(Duration d) {
		Objects.requireNonNull(d, "d");
		duration = d;
	}

	public void setPing(long ms) {
		/*
		 * Clamp the ping to [0, maxPing] so as to mitigate malicious agents
		 * increasing the window by deliberately delaying messages.
		 */
		ping = Math.max(0, Math.min(ms, maxPing));
	}

	public boolean knowsNonce(long nonce) {
		return knownNonces.containsKey(nonce);
	}

	private Instant getUpper(Instant t) {
		return t.plus(duration).plusMillis(ping);
	}

	private Instant getLower(Instant t) {
		return t.minus(duration).minusMillis(ping);
	}

	public boolean acceptMessage(Instant now, long nonce, Instant timestamp) {
		Objects.requireNonNull(now, "now");
		Objects.requireNonNull(timestamp, "timestamp");

		/* Tick us just in case. */
		tick(now);

		/* See if the timestamp is within +-duration of "now" */

		if(timestamp.isBefore(getLower(now))) {
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("{} Rejecting message {}, too old.", now, Long.toUnsignedString(nonce));
				LOGGER.trace("{}   Sent:     {}", now, timestamp);
				LOGGER.trace("{}   Received: {}", now, now);
			}
			return false;
		}

		if(timestamp.isAfter(getUpper(now))) {
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("{} Rejecting message {}, too new.", now, Long.toUnsignedString(nonce));
				LOGGER.trace("{}   Sent:     {}", now, timestamp);
				LOGGER.trace("{}   Received: {}", now, now);
			}
			return false;
		}

		/* If the nonce is less than our minimum, deny it. */
		if(Long.compareUnsigned(nonce, minimumNonce) < 0) {
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("{} Rejecting message, nonce too small ({} < {}).", now,
						Long.toUnsignedString(nonce), Long.toUnsignedString(minimumNonce));
			}
			return false;
		}

		/* If we already know about it, deny it. */
		if(knownNonces.containsKey(nonce)) {
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("{} Rejecting message, duplicate nonce {}.", now, Long.toUnsignedString(nonce));
			}
			return false;
		}

		if(LOGGER.isTraceEnabled()) {
			LOGGER.trace("{} Registering nonce {} at {}", now, Long.toUnsignedString(nonce), timestamp);
		}
		knownNonces.put(nonce, timestamp);
		return true;
	}

	public void tick(Instant now) {
		Objects.requireNonNull(now, "now");

		/* Check for any nonces that have expired. */
		knownNonces.forEach((k, v) -> {
			if(now.isAfter(getUpper(v))) {
				if(LOGGER.isTraceEnabled()) {
					LOGGER.trace("{} Expiring nonce {}", now, Long.toUnsignedString(k));
				}
				toRemove.add(k);
			}
		});

		toRemove.forEach(knownNonces::remove);
		toRemove.stream().min(Long::compareUnsigned).ifPresent(l -> {
			minimumNonce = l + 1;
			if(LOGGER.isTraceEnabled()) {
				LOGGER.trace("{} Setting minimum nonce to {}", now, Long.toUnsignedString(minimumNonce));
			}
		});
		toRemove.clear();
	}

	@Override
	public void onConfigChange(String key, String oldValue, String newValue) {
		Objects.requireNonNull(key, "key");

		switch(key) {
			case "nimrod.noncer.duration":
				setDuration(Duration.ofSeconds(ConfigListener.get(newValue, duration.getSeconds(), DEFAULT_DURATION, 0, Long.MAX_VALUE)));
				break;

			case "nimrod.noncer.max_ping_ms":
				maxPing = ConfigListener.get(newValue, maxPing, DEFAULT_MAX_PING_MS, 0, Long.MAX_VALUE);
				ping = Math.max(ping, maxPing);
				break;
		}
	}


}
