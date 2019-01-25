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

import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import java.util.UUID;

public final class AgentSubmit extends AgentMessage {

	private final NetworkJob m_Job;

	public AgentSubmit(UUID agentUuid, NetworkJob job) {
		super(agentUuid);
		m_Job = job;
	}

	@Override
	public Type getType() {
		return Type.Submit;
	}

	public NetworkJob getJob() {
		return m_Job;
	}
}
