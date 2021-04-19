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

public final class AgentLifeControl extends AgentMessage {

	public enum Operation {
		Terminate,
		Cancel
	}

	public final Operation operation;

	private AgentLifeControl(UUID agentUuid, Instant timestamp, Operation op) {
		super(agentUuid, timestamp);
		Objects.requireNonNull(op, "op");
		this.operation = op;
	}

	@Override
	public Type getType() {
		return Type.LifeControl;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private Operation op;

		public Builder operation(Operation op) {
			this.op = op;
			return this;
		}

		@Override
		public AgentLifeControl build() {
			return new AgentLifeControl(agentUuid, timestamp, op);
		}
	}
}
