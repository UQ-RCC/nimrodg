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

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public interface Job extends NimrodEntity {

	Experiment getExperiment();

	long getIndex();

	Map<String, String> getVariables();

	Instant getCreationTime();

	/**
	 * Get a list of attempts that satisfy the given criteria.
	 *
	 * This list may not be modified.
	 *
	 * @param status The statuses of the attempts.
	 * @return An immutable list of jobs that satisfy the given criteria.
	 */
	Collection<JobAttempt> filterAttempts(EnumSet<JobAttempt.Status> status);

	default Collection<JobAttempt> filterAttempts() {
		return filterAttempts(EnumSet.allOf(JobAttempt.Status.class));
	}

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
	JobAttempt.Status getStatus();

	Collection<Map<String, String>> getResults();
}
