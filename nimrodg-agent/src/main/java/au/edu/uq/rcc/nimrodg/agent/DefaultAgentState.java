/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

import java.time.Instant;
import java.util.UUID;
import javax.json.JsonObject;

public final class DefaultAgentState implements AgentState {

	private State state;
	private String queue;
	private UUID uuid;
	private int shutdownSignal;
	private ShutdownReason shutdownReason;
	private Instant lastHeardFrom;
	private Instant creationTime;
	private Instant connectionTime;
	private Instant expiryTime;
	private boolean expired;
	private String secretKey;
	private JsonObject actuatorData;

	public DefaultAgentState() {

	}

	public DefaultAgentState(AgentState as) {
		this.update(as);
	}

	public void update(AgentState as) {
		this.state = as.getState();
		this.queue = as.getQueue();
		this.uuid = as.getUUID();
		this.shutdownSignal = as.getShutdownSignal();
		this.shutdownReason = as.getShutdownReason();
		this.lastHeardFrom = as.getLastHeardFrom();
		this.creationTime = as.getCreationTime();
		this.connectionTime = as.getConnectionTime();
		this.expiryTime = as.getExpiryTime();
		this.expired = as.getExpired();
		this.secretKey = as.getSecretKey();
		this.secretKey = as.getSecretKey();
		this.actuatorData = as.getActuatorData();
	}

	@Override
	public State getState() {
		return state;
	}

	@Override
	public void setState(State state) {
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
	public ShutdownReason getShutdownReason() {
		return shutdownReason;
	}

	@Override
	public void setShutdownReason(ShutdownReason r) {
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

	public void setCreationTime(Instant time) {
		creationTime = time;
	}

	@Override
	public Instant getConnectionTime() {
		return connectionTime;
	}

	@Override
	public void setConnectionTime(Instant time) {
		connectionTime = time;
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

	@Override
	public String getSecretKey() {
		return secretKey;
	}

	@Override
	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	@Override
	public JsonObject getActuatorData() {
		return actuatorData;
	}

	@Override
	public void setActuatorData(JsonObject data) {
		actuatorData = data;
	}
}
