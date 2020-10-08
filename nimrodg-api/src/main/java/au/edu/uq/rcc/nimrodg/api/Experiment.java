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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Experiment extends NimrodEntity {

	String PROPERTY_NCPUS = "ncpus";
	String PROPERTY_MEMORY = "memory";
	String PROPERTY_WALLTIME = "walltime";
	String PROPERTY_SCRATCH = "scratch";

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

	@Deprecated
	default JobSpecification getJobSpecification() {
		return JobSpecification.empty();
	}

	Map<Task.Name, Task> getTasks();

	Task getTask(Task.Name name);

	@Deprecated
	default String getToken() {
		return getName();
	}

	boolean isPersistent();

	boolean isActive();

	default Optional<String> getProperty(String key) {
		return Optional.empty();
	}

	default Map<String, String> getProperties() {
		return Map.of();
	}

//	default long getRequiredCpus() {
//		return getProperty(PROPERTY_NCPUS).map(Long::parseLong).orElse(0L);
//	}
//
//	default long getRequiredMemory() {
//		return getProperty(PROPERTY_MEMORY).map(Long::parseLong).orElse(0L);
//	}
//
//	default long getRequiredWalltime() {
//		return getProperty(PROPERTY_WALLTIME).map(Long::parseLong).orElse(0L);
//	}
//
//	default long getRequiredScratch() {
//		return getProperty(PROPERTY_SCRATCH).map(Long::parseLong).orElse(0L);
//	}

	static String stateToString(State s) {
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

	static State stringToState(String s) {
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
