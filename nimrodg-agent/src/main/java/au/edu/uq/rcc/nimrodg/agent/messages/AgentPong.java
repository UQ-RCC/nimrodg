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
package au.edu.uq.rcc.nimrodg.agent.messages;

import au.edu.uq.rcc.nimrodg.agent.Agent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class AgentPong extends AgentMessage {

	private final Agent.ClientState clientState;

	private AgentPong(UUID agentUuid, Instant timestamp, Agent.ClientState clientState) {
		super(agentUuid, timestamp);
		Objects.requireNonNull(clientState, "clientState");
		this.clientState = clientState;
	}

	@Override
	public Type getType() {
		return Type.Pong;
	}

	public Agent.ClientState getState() {
		return clientState;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private Agent.ClientState clientState;

		public Builder clientState(Agent.ClientState clientState) {
			this.clientState = clientState;
			return this;
		}

		@Override
		public AgentPong build() {
			return new AgentPong(agentUuid, timestamp, clientState);
		}
	}
}
