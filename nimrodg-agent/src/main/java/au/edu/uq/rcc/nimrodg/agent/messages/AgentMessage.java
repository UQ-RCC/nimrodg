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
package au.edu.uq.rcc.nimrodg.agent.messages;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public abstract class AgentMessage {

	public static final int PROTOCOL_VERSION = 6;

	private final UUID agentUuid;
	private final Instant timestamp;

	public enum Type {
		Init("agent.init"),
		LifeControl("agent.lifecontrol"),
		Query("agent.query"),
		Submit("agent.submit"),
		Hello("agent.hello"),
		Shutdown("agent.shutdown"),
		Update("agent.update"),
		Ping("agent.ping"),
		Pong("agent.pong");

		public final String typeString;

		Type(String typeString) {
			this.typeString = typeString;
		}
	}

	protected AgentMessage(UUID agentUuid, Instant timestamp) {
		Objects.requireNonNull(agentUuid, "agentUuid");
		Objects.requireNonNull(timestamp, "timestamp");

		this.agentUuid = agentUuid;
		this.timestamp = timestamp;
	}

	public UUID getAgentUUID() {
		return agentUuid;
	}

	public abstract Type getType();

	public final int getVersion() {
		return PROTOCOL_VERSION;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	@SuppressWarnings("unchecked")
	public static abstract class Builder<T extends Builder> {
		protected UUID agentUuid;
		protected Instant timestamp;

		public T agentUuid(UUID uuid) {
			this.agentUuid = uuid;
			return (T)this;
		}

		public T timestamp(Instant timestamp) {
			this.timestamp = timestamp;
			return (T)this;
		}

		public abstract AgentMessage build();
	}
}
