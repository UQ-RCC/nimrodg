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
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import java.security.cert.Certificate;
import java.util.EnumSet;
import java.util.Map;

/**
 * This is the Nimrod/G API intended for use by the Experiment Master.
 */
public interface NimrodMasterAPI extends NimrodAPI {

	/**
	 * Create an actuator for the specified resource node.
	 *
	 * @param ops The master operations.
	 * @param node The resource node to be managed by the actuator.
	 * @param amqpUri The resolved AMQP URI.
	 * @param certs The list of certificates to use. May be null.
	 * @return The new actuator instance.
	 * @throws IllegalArgumentException if node isn't a root resource.
	 * @throws IOException if an I/O error occurs when creating the actuator.
	 */
	Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs) throws IllegalArgumentException, IOException;

	default Actuator createActuator(Actuator.Operations ops, Resource node, Certificate[] certs) throws IllegalArgumentException, IOException {
		return createActuator(ops, node, node.getAMQPUri(), certs);
	}

	ResourceTypeInfo getResourceTypeInfo(Resource node);

	boolean isResourceCapable(Resource res, Experiment exp);

	void addResourceCaps(Resource node, Experiment exp);

	void removeResourceCaps(Resource node, Experiment exp);

	@Override
	AgentState getAgentByUUID(UUID uuid);

	@Override
	Collection<AgentState> getResourceAgents(Resource node);

	AgentState addAgent(Resource node, AgentState agent);

	void updateAgent(AgentState agent);

	void updateExperimentState(Experiment exp, Experiment.State state);

	List<JobAttempt> createJobAttempts(Collection<Job> jobs);

	void startJobAttempt(JobAttempt att, UUID agentUuid);

	void finishJobAttempt(JobAttempt att, boolean failed);

	Map<Job, Collection<JobAttempt>> filterJobAttempts(Experiment exp, EnumSet<JobAttempt.Status> status);

	/**
	 * Add a command result.
	 *
	 * @param att The job attempt to add the result for.
	 * @param status The status of the command.
	 * @param index The command index. If negative, assume the next command in the sequence.
	 * @param time The time taken in seconds.
	 * @param retval The return value.
	 * @param message A human-readable error message.
	 * @param errcode The error code.
	 * @param stop Has execution stopped?
	 * @return
	 */
	CommandResult addCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop);

	Collection<NimrodMasterEvent> pollMasterEvents();
}
