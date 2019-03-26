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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import java.time.Instant;
import java.util.UUID;

public class TempAgent {

	public final long id;
	public final Agent.State state;
	public final String queue;
	public final UUID uuid;
	public final int shutdownSignal;
	public final AgentShutdown.Reason shutdownReason;
	public final Instant created;
	public final Instant lastHeardFrom;
	public final Instant expiryTime;
	public final boolean expired;
	public final Long location;

	public TempAgent(long id, Agent.State state, String queue, UUID uuid, int shutdownSignal, AgentShutdown.Reason shutdownReason, Instant created, Instant lastHeardFrom, Instant expiryTime, boolean expired, Long location) {
		this.id = id;
		this.state = state;
		this.queue = queue;
		this.uuid = uuid;
		this.shutdownSignal = shutdownSignal;
		this.shutdownReason = shutdownReason;
		this.created = created;
		this.lastHeardFrom = lastHeardFrom;
		this.expiryTime = expiryTime == null ? Instant.MAX : expiryTime;
		this.expired = expired;
		this.location = location;
	}

	public Impl create() {
		return new Impl(this);
	}

	public static final class Impl extends DefaultAgentState {

		private Impl(TempAgent ta) {
			this.setState(ta.state);
			this.setQueue(ta.queue);
			this.setUUID(ta.uuid);
			this.setShutdownSignal(ta.shutdownSignal);
			this.setShutdownReason(ta.shutdownReason);
			this.setCreationTime(ta.created);
			this.setLastHeardFrom(ta.lastHeardFrom);
			this.setExpiryTime(ta.expiryTime);
		}
	}

}
