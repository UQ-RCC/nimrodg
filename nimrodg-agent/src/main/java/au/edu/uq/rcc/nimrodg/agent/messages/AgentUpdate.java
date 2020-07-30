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

import java.util.Objects;
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

	private final UUID jobUuid;
	private final CommandResult_ commandResult;
	private final Action action;

	private AgentUpdate(UUID agentUuid, UUID jobUuid, CommandResult_ result, Action action) {
		super(agentUuid);
		Objects.requireNonNull(jobUuid, "jobUuid");
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(action, "action");
		this.jobUuid = jobUuid;
		this.commandResult = result;
		this.action = action;
	}

	@Override
	public Type getType() {
		return Type.Update;
	}

	public UUID getJobUUID() {
		return jobUuid;
	}

	public CommandResult_ getCommandResult() {
		return commandResult;
	}

	public Action getAction() {
		return action;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private UUID jobUuid;
		private CommandResult_ commandResult;
		private Action action;

		public Builder jobUuid(UUID jobUuid) {
			this.jobUuid = jobUuid;
			return this;
		}

		public Builder commandResult(CommandResult_ commandResult) {
			this.commandResult = commandResult;
			return this;
		}

		public Builder action(Action action) {
			this.action = action;
			return this;
		}

		@Override
		public AgentUpdate build() {
			return new AgentUpdate(agentUuid, jobUuid, commandResult, action);
		}
	}
}
