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

public class AgentHello extends AgentMessage {

	public final String queue;

	private AgentHello(UUID agentUuid, Instant timestamp, String queue) {
		super(agentUuid, timestamp);
		Objects.requireNonNull(queue, "queue");
		this.queue = queue;
	}

	@Override
	public Type getType() {
		return Type.Hello;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private String queue;

		public Builder queue(String queue) {
			this.queue = queue;
			return this;
		}

		@Override
		public AgentHello build() {
			return new AgentHello(agentUuid, timestamp, queue);
		}
	}
}
