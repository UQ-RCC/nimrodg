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

import au.edu.uq.rcc.nimrodg.api.CommandResult;
import java.util.UUID;

public final class AgentUpdate extends AgentMessage {

	public enum Action {
		Continue,
		Stop
	}

	public static final class CommandResult_ {

		public final CommandResult.CommandResultStatus status;
		public final long index;
		public final float time;
		public final int retVal;
		public final String message;
		public final int errorCode;

		public CommandResult_(CommandResult.CommandResultStatus status, long index, float time, int retVal, String message, int errorCode) {
			this.status = status;
			this.index = index;
			this.time = time;
			this.retVal = retVal;
			this.message = message;
			this.errorCode = errorCode;
		}
	}

	private final UUID m_JobUUID;
	private final CommandResult_ m_CommandResult;
	private final Action m_Action;

	public AgentUpdate(UUID agentUuid, UUID jobUuid, CommandResult_ result, Action action) {
		super(agentUuid);
		m_JobUUID = jobUuid;
		m_CommandResult = result;
		m_Action = action;
	}

	@Override
	public Type getType() {
		return Type.Update;
	}

	public UUID getJobUUID() {
		return m_JobUUID;
	}

	public CommandResult_ getCommandResult() {
		return m_CommandResult;
	}

	public Action getAction() {
		return m_Action;
	}
}
