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
import java.util.Set;

public interface Experiment extends NimrodEntity {

	enum State {
		STOPPED,
		STARTED,
		PERSISTENT
	}

	String getName();

	State getState();

	String getWorkingDirectory();

	Instant getCreationTime();

	Set<String> getVariables();

	/**
	 * Get a list of jobs that satisfy the given criteria.
	 *
	 * This list may not be modified.
	 *
	 * @param status The statuses of the jobs.
	 * @param start The lowest possible starting number for the job.
	 * @param limit The maximum number of jobs to return. Set to 0 for no limit.
	 * @return An immutable list of jobs that satisfy the given criteria.
	 */
	Collection<Job> filterJobs(EnumSet<JobAttempt.Status> status, long start, int limit);

	Collection<Task> getTasks();

	Task getTask(Task.Name name);

	String getToken();

	boolean isPersistent();

	boolean isActive();

	public static String stateToString(State s) {
		switch(s) {
			case STOPPED:
				return "STOPPED";
			case STARTED:
				return "STARTED";
			case PERSISTENT:
				return "PERSISTENT";
		}

		throw new IllegalArgumentException();
	}

	public static State stringToState(String s) {
		switch(s) {
			case "STOPPED":
				return State.STOPPED;
			case "STARTED":
				return State.STARTED;
			case "PERSISTENT":
				return State.PERSISTENT;
		}

		throw new IllegalArgumentException();
	}
}
