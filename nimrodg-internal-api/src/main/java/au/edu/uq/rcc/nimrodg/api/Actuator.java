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
import java.util.Arrays;
import java.util.UUID;
import javax.json.JsonObject;

public interface Actuator extends AutoCloseable {

	interface Operations extends AgentProvider {

		/**
		 * Report an out-of-band failure of the agent. Some examples of an out-of-band failure are:
		 * <ul>
		 * <li>A local agent is SIGKILL'd and can't send a shutdown message.
		 * The actuator was waiting on process termination and can report this to the master knows.
		 * </li>
		 * </ul>
		 *
		 * @param act This actuator.
		 * @param uuid The UUID of the agent. Must be owned by this actuator.
		 * @param reason The reason this agent failed.
		 * @param signal The signal that caused this agent to fail.
		 *
		 * @throws IllegalArgumentException if uuid is null or is not owned by this actuator.
		 */
		void reportAgentFailure(Actuator act, UUID uuid, AgentInfo.ShutdownReason reason, int signal) throws IllegalArgumentException;

		NimrodConfig getConfig();

		String getSigningAlgorithm();

		int getAgentCount(Resource res);
	}

	Resource getResource() throws NimrodException;

	class LaunchResult {

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

	class Request {
		public final UUID uuid;
		public final String secretKey;

		public Request(UUID uuid, String secretKey) {
			this.uuid = uuid;
			this.secretKey = secretKey;
		}

		public static Request forAgent(UUID uuid, String secretKey) {
			return new Request(uuid, secretKey);
		}
	}

	/**
	 *
	 * @param requests The list of agent launch requests.
	 * @throws IOException if the actuator is unable to communicate with the resource, or an IO error occurs.
	 */
	LaunchResult[] launchAgents(Request... requests) throws IOException;

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
	 * It is possible that an agent connects and is assigned work before the actuator has finished launching a batch.
	 * In this case, this will be called as soon as is convenient. No assumptions as to the agent state should be made.
	 *
	 * Non-state data such as expiration time and actuator-specific data may be set.
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

	enum AdoptStatus {
		/**
		 * Agent was adopted.
		 */
		Adopted,
		/**
		 * Agent was rejected (not ours).
		 */
		Rejected,
		/**
		 * Agent was ours, but doesn't exist anymore.
		 *
		 * The actuator should not add the agent to any internal data structures.
		 */
		Stale
	}

	AdoptStatus adopt(AgentState state);

	/**
	 * Agent status from an actuator's POV.
	 */
	enum AgentStatus {
		/**
		 * Agent is still launching. May be stuck in a queue.
		 */
		Launching,
		/**
		 * Agent has launched, but not connected yet.
		 */
		Launched,
		/**
		 * Agent has connected.
		 * Implies {@link Actuator#notifyAgentConnection(AgentState)} has been called.
		 */
		Connected,
		/**
		 * Agent has disconnected.
		 * Implies {@link Actuator#notifyAgentDisconnection(UUID)} has been called.
		 */
		Disconnected,
		/**
		 * Agent is dead.
		 * It is not known if {@link Actuator#notifyAgentDisconnection(UUID)} has been called.
		 */
		Dead,
		/**
		 * Unknown. The agent may not be ours, or we have stopped tracking it.
		 */
		Unknown
	}

	default AgentStatus queryStatus(UUID uuid) {
		return AgentStatus.Unknown;
	}
}
