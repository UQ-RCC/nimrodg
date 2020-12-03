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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;

import java.time.Instant;
import java.util.UUID;
import javax.json.JsonObject;

public class TempAgent {

	public final long id;
	public final AgentInfo.State state;
	public final String queue;
	public final UUID uuid;
	public final int shutdownSignal;
	public final AgentInfo.ShutdownReason shutdownReason;
	public final Instant created;
	public final Instant connectedAt;
	public final Instant lastHeardFrom;
	public final Instant expiryTime;
	public final boolean expired;
	public final String secretKey;
	public final Long location;
	public final JsonObject actuatorData;

	public TempAgent(long id, AgentInfo.State state, String queue, UUID uuid, int shutdownSignal, AgentInfo.ShutdownReason shutdownReason, Instant created, Instant connectedAt, Instant lastHeardFrom, Instant expiryTime, boolean expired, String secretKey, Long location, JsonObject actuatorData) {
		this.id = id;
		this.state = state;
		this.queue = queue;
		this.uuid = uuid;
		this.shutdownSignal = shutdownSignal;
		this.shutdownReason = shutdownReason;
		this.created = created;
		this.connectedAt = connectedAt;
		this.lastHeardFrom = lastHeardFrom;
		this.expiryTime = expiryTime == null ? Instant.MAX : expiryTime;
		this.expired = expired;
		this.secretKey = secretKey;
		this.location = location;
		this.actuatorData = actuatorData;
	}

	public Impl create() {
		return new Impl(this);
	}

	public static final class Impl implements AgentState {

		public final TempAgent ta;

		private Impl(TempAgent ta) {
			this.ta = ta;
		}

		@Override
		public AgentInfo.State getState() {
			return ta.state;
		}

		@Override
		public void setState(AgentInfo.State state) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getQueue() {
			return ta.queue;
		}

		@Override
		public void setQueue(String q) {
			throw new UnsupportedOperationException();
		}

		@Override
		public UUID getUUID() {
			return ta.uuid;
		}

		@Override
		public void setUUID(UUID uuid) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getShutdownSignal() {
			return ta.shutdownSignal;
		}

		@Override
		public void setShutdownSignal(int s) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AgentInfo.ShutdownReason getShutdownReason() {
			return ta.shutdownReason;
		}

		@Override
		public void setShutdownReason(AgentInfo.ShutdownReason r) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant getCreationTime() {
			return ta.created;
		}

		@Override
		public Instant getConnectionTime() {
			return ta.connectedAt;
		}

		@Override
		public void setConnectionTime(Instant time) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant getLastHeardFrom() {
			return ta.lastHeardFrom;
		}

		@Override
		public void setLastHeardFrom(Instant time) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant getExpiryTime() {
			return ta.expiryTime;
		}

		@Override
		public void setExpiryTime(Instant time) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean getExpired() {
			return ta.expired;
		}

		@Override
		public void setExpired(boolean expired) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getSecretKey() {
			return ta.secretKey;
		}

		@Override
		public void setSecretKey(String secretKey) {
			throw new UnsupportedOperationException();
		}

		@Override
		public JsonObject getActuatorData() {
			return ta.actuatorData;
		}

		@Override
		public void setActuatorData(JsonObject data) {
			throw new UnsupportedOperationException();
		}
	}

}
