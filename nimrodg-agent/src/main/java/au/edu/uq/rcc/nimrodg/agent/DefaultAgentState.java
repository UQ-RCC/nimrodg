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
package au.edu.uq.rcc.nimrodg.agent;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import java.time.Instant;
import java.util.UUID;

public class DefaultAgentState implements AgentState {

	private Agent.State state;
	private String queue;
	private UUID uuid;
	private int shutdownSignal;
	private AgentShutdown.Reason shutdownReason;
	private Instant lastHeardFrom;
	private Instant creationTime;
	private Instant expiryTime;
	private boolean expired;

	@Override
	public Agent.State getState() {
		return state;
	}

	@Override
	public void setState(Agent.State state) {
		this.state = state;
	}

	@Override
	public String getQueue() {
		return queue;
	}

	@Override
	public void setQueue(String q) {
		queue = q;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public void setUUID(UUID uuid) {
		this.uuid = uuid;
	}

	@Override
	public int getShutdownSignal() {
		return shutdownSignal;
	}

	@Override
	public void setShutdownSignal(int s) {
		shutdownSignal = s;
	}

	@Override
	public AgentShutdown.Reason getShutdownReason() {
		return shutdownReason;
	}

	@Override
	public void setShutdownReason(AgentShutdown.Reason r) {
		shutdownReason = r;
	}

	@Override
	public Instant getLastHeardFrom() {
		return lastHeardFrom;
	}

	@Override
	public void setLastHeardFrom(Instant time) {
		lastHeardFrom = time;
	}

	@Override
	public Instant getCreationTime() {
		return creationTime;
	}

	@Override
	public void setCreationTime(Instant time) {
		creationTime = time;
	}

	@Override
	public Instant getExpiryTime() {
		return expiryTime == null ? Instant.MAX : expiryTime;
	}

	@Override
	public void setExpiryTime(Instant time) {
		expiryTime = time;
	}

	@Override
	public boolean getExpired() {
		return expired;
	}

	@Override
	public void setExpired(boolean e) {
		expired = e;
	}
}
