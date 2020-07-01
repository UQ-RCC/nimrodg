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
package au.edu.uq.rcc.nimrodg.master.sched;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.master.ConfigListener;
import au.edu.uq.rcc.nimrodg.master.Master;
import java.util.Optional;
import java.util.Set;

public interface AgentScheduler extends ConfigListener {

	interface Operations {

		/**
		 * Request agents be spawned on the given resource. The agent instances will be passed through the
		 * {@link AgentScheduler#onAgentConnect(UUID, Agent)} method.
		 *
		 * @param res The resource to spawn the agents on.
		 * @param num The number of agents to spawn.
		 *
		 * @return The initial UUID of the agents.
		 */
		UUID[] launchAgents(Resource res, int num);

		void terminateAgent(Agent agent);

		/**
		 * Run a managed job.
		 *
		 *
		 * @param att
		 * @param agent
		 * @return
		 */
		UUID runJob(JobAttempt att, Agent agent);

		/**
		 * Run an unmanaged job.
		 *
		 * Unmanaged jobs have no participation in the system and are intended to run other tasks such as setup.
		 *
		 * The job is uniquely identified by the {@link NetworkJob#getUUID()} field. Submitting an unmanaged job with
		 * the same UUID as an already-executing one is forbidden. After results have been received, or the job fails,
		 * UUIDs may be reused.
		 *
		 * @param job
		 * @param agent
		 *
		 * @throws IllegalArgumentException if there's already an unmanaged job running with the same UUID.
		 */
		void runUnmanagedJob(NetworkJob job, Agent agent) throws IllegalArgumentException;

		void cancelCurrentJob(Agent agent);

		Collection<Resource> getResources();

		Collection<Resource> getAssignedResources(Experiment exp);

		Optional<NimrodURI> resolveTransferUri(Resource res, Experiment exp);

		boolean isResourceCapable(Resource node, Experiment exp);

		void addResourceCaps(Resource node, Experiment exp);

		Resource getAgentResource(Agent agent);

		List<Agent> getResourceAgents(Resource node);

		enum FailureReason {
			EXPIRED,
			CRASHED
		}

		/**
		 * Report a job failure.
		 *
		 * Possible reason for failure are:
		 * <ul>
		 * <li>SIGTERM,</li>
		 * <li>Too many missed heartbeats.</li>
		 * </ul>
		 *
		 * @param att The job attempt that failed.
		 * @param agent The agent that was running the job.
		 * @param r The reason for failure.
		 */
		void reportJobFailure(JobAttempt att, Agent agent, FailureReason r);
	}

	/**
	 * Re-synchronise state with the master.
	 * @param agents A set of all agents.
	 * @param jobs A set of the currently-running jobs.
	 */
	void resync(Set<Agent> agents, Set<Master.RunningJob> jobs);

	/**
	 * Set the agent operations instance. This may only set once.
	 *
	 * @param ops The operations instance.
	 * @throws IllegalArgumentException If ops is null or this function has already been called with a non-null
	 * argument.
	 */
	void setAgentOperations(Operations ops) throws IllegalArgumentException;

	/**
	 * Called by the master when an agent's state updates.
	 *
	 * This should be used for accounting purposes only and any operations should be deferred until next tick.
	 *
	 * @param agent The agent instance.
	 * @param node The node the agent's on.
	 * @param oldState The old state. If null, this is the initial transition.
	 * @param newState The new state.
	 */
	void onAgentStateUpdate(Agent agent, Resource node, Agent.State oldState, Agent.State newState);

	void onAgentLaunchFailure(UUID uuid, Resource node, Throwable t);

	void onAgentExpiry(UUID uuid);

	void onJobLaunchFailure(JobAttempt att, NetworkJob job, Agent agent, boolean hardFailure, Throwable t);

	void onJobRun(JobAttempt att);

	void onJobCancel(JobAttempt att);

	void onUnmanagedJobUpdate(NetworkJob job, AgentUpdate au, Agent agent);

	boolean tick();
}
