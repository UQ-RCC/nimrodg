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

import java.util.UUID;

public abstract class AgentMessage {

	private final UUID m_AgentUUID;

	public enum Type {
		Init,
		LifeControl,
		Query,
		Submit,
		Hello,
		Shutdown,
		Update,
		Ping,
		Pong
	}

	protected AgentMessage(UUID agentUuid) {
		m_AgentUUID = agentUuid;
	}

	public UUID getAgentUUID() {
		return m_AgentUUID;
	}

	public abstract Type getType();

	public String getTypeString() {
		return getTypeString(getType());
	}

	public static String getTypeString(Type t) {
		switch(t) {
			case Hello:
				return "agent.hello";
			case Init:
				return "agent.init";
			case LifeControl:
				return "agent.lifecontrol";
			case Query:
				return "agent.query";
			case Shutdown:
				return "agent.shutdown";
			case Submit:
				return "agent.submit";
			case Update:
				return "agent.update";
			case Ping:
				return "agent.ping";
			case Pong:
				return "agent.pong";
		}
		throw new IllegalArgumentException();
	}
}
