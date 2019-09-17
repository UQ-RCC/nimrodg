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
package au.edu.uq.rcc.nimrodg.api;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import javax.json.JsonObject;

public interface Actuator extends AutoCloseable {

	interface Operations {

		/**
		 * Report an out-of-band failure of the agent.Some examples of an out-of-band failure are:
		 * <ul>
		 * <li>A local agent is SIGKILL'd and can't send a shutdown message.The actuator was waiting on process
		 * termination and can report this to the master knows.</li>
		 * </ul>
		 *
		 * @param act This actuator.
		 * @param uuid The UUID of the agent. Must be owned by this actuator.
		 * @param reason The reason this agent failed.
		 * @param signal The signal that caused this agent to fail.
		 *
		 * @throws IllegalArgumentException if uuid is null or is not owned by this actuator.
		 */
		void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException;

		NimrodMasterAPI getNimrod();
	}

	public Resource getResource() throws NimrodAPIException;

	public static class LaunchResult {

		public final Resource node;
		public final Throwable t;
		public final Instant expiryTime;
		public final JsonObject actuatorData;

		public LaunchResult(Resource node, Throwable t) {
			this(node, t, null, null);
		}

		public LaunchResult(Resource node, Throwable t, Instant expiryTime, JsonObject actuatorData) {
			this.node = node;
			this.t = t;
			this.expiryTime = expiryTime;
			this.actuatorData = actuatorData;
		}

	}

	@Deprecated
	default Resource launchAgent(UUID uuid) throws IOException {
		LaunchResult lr = launchAgents(new UUID[]{uuid})[0];

		if(lr.t != null) {
			try {
				throw lr.t;
			} catch(IOException | RuntimeException e) {
				throw e;
			} catch(Throwable t) {
				throw new RuntimeException(t);
			}
		}

		return lr.node;
	}

	/**
	 *
	 * @param uuid The initial UUIDs of the agent.
	 * @return
	 * @throws IOException if the actuator is unable to communicate with the resource, or an IO error occurs.
	 */
	LaunchResult[] launchAgents(UUID[] uuid) throws IOException;

	/**
	 * Force terminate an agent. This is only ever called as a last-ditch-effort after normal methods have failed.
	 *
	 * Cleanup should attempted, but should not necessarily be successful.
	 *
	 *
	 * @param uuid The list of agent UUIDs to terminate.
	 */
	void forceTerminateAgent(UUID[] uuid);

	boolean isClosed();

	@Override
	void close() throws IOException;

	/**
	 * Called by the master when an agent connects.
	 *
	 * When this is called, the agent's state hasn't yet been committed yet, so it may provide initial values for
	 * parameters.
	 *
	 * @param state The agent state.
	 */
	void notifyAgentConnection(AgentState state);

	void notifyAgentDisconnection(UUID uuid);

	/**
	 * Can the given resource node handle num more agents?
	 *
	 * @param num The number of agents.
	 * @return If the given node is capable of handling at least num more agents, this should return true. Otherwise,
	 * false.
	 * @throws IllegalArgumentException if node is not a node we manage.
	 */
	boolean canSpawnAgents(int num) throws IllegalArgumentException;

	boolean adopt(AgentState state);
}
