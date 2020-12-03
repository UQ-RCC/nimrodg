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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static au.edu.uq.rcc.nimrodg.api.AgentInfo.ShutdownReason;

public class AgentShutdown extends AgentMessage {

	public final ShutdownReason reason;
	public final int signal;

	private AgentShutdown(UUID agentUuid, Instant timestamp, ShutdownReason reason, int signal) {
		super(agentUuid, timestamp);
		Objects.requireNonNull(reason, "reason");
		this.reason = reason;
		this.signal = signal;
	}

	@Override
	public Type getType() {
		return Type.Shutdown;
	}

	public static class Builder extends AgentMessage.Builder<Builder> {
		private ShutdownReason reason;
		private int signal;

		public Builder reason(ShutdownReason reason) {
			this.reason = reason;
			return this;
		}

		public Builder signal(int signal) {
			this.signal = signal;
			return this;
		}

		@Override
		public AgentShutdown build() {
			return new AgentShutdown(agentUuid, timestamp, reason, signal);
		}
	}

	public static String reasonToString(ShutdownReason r) {
		switch(r) {
			case HostSignal:
				return "HostSignal";
			case Requested:
				return "Requested";
		}

		throw new IllegalArgumentException();
	}

	public static ShutdownReason reasonFromString(String s) {
		switch(s) {
			case "HostSignal":
				return ShutdownReason.HostSignal;
			case "Requested":
				return ShutdownReason.Requested;
		}

		throw new IllegalArgumentException();
	}
}
