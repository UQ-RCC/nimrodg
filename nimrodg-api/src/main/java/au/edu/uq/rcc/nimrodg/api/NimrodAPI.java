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
package au.edu.uq.rcc.nimrodg.api;

import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;

import javax.json.JsonStructure;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The public API for Nimrod/G.
 */
public interface NimrodAPI extends AgentProvider, AutoCloseable {

	final class APICaps {

		/**
		 * Does this API implementation support Master capabilities.
		 */
		public final boolean master;

		/**
		 * Does this API implementation support Serve capabilities.
		 */
		public final boolean serve;

		public APICaps(boolean master, boolean serve) {
			this.master = master;
			this.serve = serve;
		}
	}

	/**
	 * Get the capabilities of this API implementation.
	 * @return The capabilities of this API implementation.
	 */
	APICaps getAPICaps();

	Collection<Experiment> getExperiments();

	Experiment getExperiment(String name);

	Experiment addExperiment(String name, CompiledRun run);

	/**
	 * Delete an experiment.
	 *
	 * @param exp The experiment instance to delete.
	 */
	void deleteExperiment(Experiment exp);

	Job addSingleJob(Experiment exp, Map<String, String> values);

	Collection<Job> addJobs(Experiment exp, Collection<Map<String, String>> values);


	/**
	 * Get a list of jobs that satisfy the given criteria.
	 *
	 * This list may not be modified.
	 *
	 * @param exp The experiment instance.
	 * @param status The statuses of the jobs.
	 * @param start The lowest possible starting number for the job.
	 * @param limit The maximum number of jobs to return. Set to 0 for no limit.
	 * @return An immutable list of jobs that satisfy the given criteria.
	 */
	Collection<Job> filterJobs(Experiment exp, EnumSet<JobAttempt.Status> status, long start, int limit);

	default List<JobAttempt> filterJobAttempts(Job job, EnumSet<JobAttempt.Status> status) {
		return this.filterJobAttempts(List.of(job), status).getOrDefault(job, List.of());
	}

	/**
	 * Get a list of job attempts that satisfy the given criteria.
	 *
	 * @param jobs The set of jobs whose attempts to query.
	 * @param status The statuses of the attempts.
	 *
	 * @return A list of attempts that satisfy the given criteria.
	 */
	Map<Job, List<JobAttempt>> filterJobAttempts(Collection<Job> jobs, EnumSet<JobAttempt.Status> status);

	Map<JobAttempt, List<CommandResult>> getCommandResults(Collection<JobAttempt> attempts);

	/**
	 * Get the status of the job.
	 *
	 * A job will have the following states under certain conditions:
	 * <ul>
	 * <li>{@link JobAttempt.Status#NOT_RUN}.
	 *	<ul>
	 *		<li>There are no attempts, or</li>
	 *		<li>All attempts have the state {@link JobAttempt.Status#NOT_RUN}.</li>
	 *	</ul>
	 * </li>
	 * <li>{@link JobAttempt.Status#RUNNING}
	 *	<ul>
	 *		<li>There is at least one attempt with the state {@link JobAttempt.Status#RUNNING}, and</li>
	 *		<li>No attempts have the state {@link JobAttempt.Status#COMPLETED}.</li>
	 *	</ul>
	 * </li>
	 * <li>{@link JobAttempt.Status#FAILED}
	 *	<ul>
	 *		<li>There is at least one attempt with the state {@link JobAttempt.Status#FAILED}, and</li>
	 *		<li>No attempts have the state {@link JobAttempt.Status#COMPLETED}, and</li>
	 *		<li>No attempts have the state {@link JobAttempt.Status#RUNNING}.</li>
	 *	</ul>
	 * </li>
	 * <li>{@link JobAttempt.Status#COMPLETED}
	 *	<ul>
	 *		<li>There is at least one attempt with the state {@link JobAttempt.Status#COMPLETED}.</li>
	 *	</ul>
	 * </li>
	 * </ul>
	 *
	 * @return The status of the job.
	 */
	default JobAttempt.Status getJobStatus(Job j) {
		return getJobStatuses(List.of(j)).get(0);
	}

	List<JobAttempt.Status> getJobStatuses(Collection<Job> jobs);

	NimrodConfig getConfig();

	void updateConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri);

	/**
	 * Get a property value.
	 *
	 * @param key The key.
	 * @return The property value, or null if none exists.
	 */
	Optional<String> getProperty(String key);

	/**
	 * Set a property value.
	 *
	 * @param key The key.
	 * @param value The value. If null or "", delete the property.
	 * @return The old value of the property, if any.
	 */
	Optional<String> setProperty(String key, String value);

	Map<String, String> getProperties();

	/**
	 * Lookup a resource by name.
	 *
	 * @param resourcePath The resource name. This must be an identifier,
	 * @return The resource found, or null if no resource exists.
	 */
	Resource getResource(String resourcePath);

	/**
	 * Add a resource.
	 *
	 * @param name The resource name. Must be an identifier. {@literal ^[a-zA-Z0-9_]+$}.
	 * @param type The type name of the resource.
	 * @param config The resource configuration.
	 * @param amqpUri The URI to connect back to the message queue. If null, use the global defaults.
	 * @param txUri The URI to connect back to the central file store. If null, use the global defaults.
	 * @return The newly-added resource node.
	 * @throws IllegalArgumentException if any of the arguments are null.
	 * @throws IllegalArgumentException if type does not represent a valid resource type.
	 */
	Resource addResource(String name, String type, JsonStructure config, NimrodURI amqpUri, NimrodURI txUri) throws IllegalArgumentException;

	/**
	 * Delete a resource.
	 *
	 * The resource must not be assigned to any experiments before deletion.
	 *
	 * @param resource The resource instance to delete.
	 */
	void deleteResource(Resource resource);

	Collection<Resource> getResources();

	/**
	 * Get all the resource nodes that are assigned to the given experiment.
	 *
	 * Returned nodes may not always be at the bottom of the resource hierarchy, so care should be taken to account for
	 * this. It is guaranteed that if a resource node is assigned to an experiment, then all its children are assigned.
	 *
	 * @param exp The experiment.
	 * @return The list of resource nodes assigned to the given experiment.
	 */
	Collection<Resource> getAssignedResources(Experiment exp);

	default void assignResource(Resource res, Experiment exp) {
		assignResource(res, exp, (NimrodURI)null);
	}

	void assignResource(Resource res, Experiment exp, NimrodURI txUri);

	@Deprecated
	default void assignResource(Resource res, Experiment exp, Optional<NimrodURI> txUri) {
		assignResource(res, exp, txUri.orElse(null));
	}

	void unassignResource(Resource res, Experiment exp);

	default boolean isResourceAssigned(Resource res, Experiment exp) {
		return getAssignmentStatus(res, exp).isPresent();
	}

	Optional<NimrodURI> getAssignmentStatus(Resource res, Experiment exp);

	Collection<ResourceTypeInfo> getResourceTypeInfo();

	ResourceTypeInfo getResourceTypeInfo(String name);

	/**
	 * Add a resource type with a given implementation class.
	 *
	 * If such a mapping already exists, it is overwritten.
	 *
	 * @param name The resource type name. Must not be NULL.
	 * @param clazz The canonical name of the implementation class. Must not be NULL.
	 *              This class must implement the {@link ResourceType} interface and have
	 *              a default public constructor.
	 * @return A {@link ResourceTypeInfo} structure representing the new/updated
	 *         resource type;
	 */
	ResourceTypeInfo addResourceType(String name, String clazz);

	/**
	 * Remove a resource type.
	 *
	 * This function will throw if an existing resource references the to-be-removed type.
	 *
	 * @param name The resource type name. Must not be NULL.
	 * @return true if a mapping was removed. Otherwise false.
	 */
	boolean deleteResourceType(String name);

	AgentInfo getAgentByUUID(UUID uuid);

	Resource getAgentResource(UUID uuid);

	Collection<? extends AgentInfo> getResourceAgents(Resource node);

	/**
	 * Map an agent platform string to an agent binary.
	 *
	 * If such a mapping already exists, it is overwritten.
	 *
	 * @param platformString The agent platform string. Must not be NULL.
	 * @param path The path to the agent binary on the machine where the experiment
	 *             master is running. Must not be NULL.
	 */
	void addAgentPlatform(String platformString, Path path);

	/**
	 * Unmap an agent platform string from an agent binary.
	 *
	 * If no mapping exists, no action is taken.
	 *
	 * @param platformString The agent platform string. Must not be NULL.
	 * @return true if a mapping was removed. Otherwise false.
	 */
	boolean deleteAgentPlatform(String platformString);

	/**
	 * Map a POSIX (system, machine) tuple to an agent platform.
	 *
	 * If such a mapping already exists, it is overwritten.
	 *
	 * @param platformString The agent platform string. Must not be NULL.
	 * @param pair The POSIX (system, machine) tuple to map. Must not be NULL.
	 */
	void mapAgentPosixPlatform(String platformString, MachinePair pair);

	/**
	 * Unmap a POSIX (system, machine) tuple from an agent platform.
	 *
	 * If no mapping exists, no action is taken.
	 *
	 * @param pair The POSIX (system, machine) tuple to unmap. Must not be NULL.
	 * @return true if a mapping was removed. Otherwise false.
	 */
	boolean unmapAgentPosixPlatform(MachinePair pair);

	@Override
	void close() throws NimrodException;
}
