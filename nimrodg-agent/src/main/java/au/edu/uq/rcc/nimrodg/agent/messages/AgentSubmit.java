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

public final class AgentSubmit extends AgentMessage {

	private final NetworkJob m_Job;

	private AgentSubmit(UUID agentUuid, Instant timestamp, NetworkJob job) {
		super(agentUuid, timestamp);
		Objects.requireNonNull(job, "job");
		m_Job = job;
	}

	@Override
	public Type getType() {
		return Type.Submit;
	}

	public NetworkJob getJob() {
		return m_Job;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private NetworkJob job;

		public Builder job(NetworkJob job) {
			this.job = job;
			return this;
		}

		@Override
		public AgentSubmit build() {
			return new AgentSubmit(agentUuid, timestamp, job);
		}
	}
}
