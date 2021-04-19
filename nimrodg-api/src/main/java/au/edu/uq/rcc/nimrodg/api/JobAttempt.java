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

import java.time.Instant;
import java.util.UUID;

public interface JobAttempt {

	/**
	 * Valid state transitions:
	 * <ul>
	 * <li>NOT_RUN -&gt; RUNNING</li>
	 * <li>NOT_RUN -&gt; FAILED</li>
	 * <li>RUNNING -&gt; COMPLETED</li>
	 * <li>RUNNING -&gt; FAILED</li>
	 * </ul>
	 */
	enum Status {
		/**
		 * Job hasn't started.
		 *
		 * <ul>
		 * <li>{@link #getStartTime()} will return null.</li>
		 * <li>{@link #getFinishTime()} will return null.</li>
		 * <li>{@link #getAgentUUID()} will return null.</li>
		 * </ul>
		 */
		NOT_RUN,
		/**
		 * Job is running.
		 *
		 * <ul>
		 * <li>{@link #getFinishTime()} will return null.</li>
		 * </ul>
		 */
		RUNNING,
		/**
		 * Job has completed.
		 */
		COMPLETED,
		/**
		 * Job has failed.
		 *
		 * <ul>
		 * <li>{@link #getAgentUUID()} may return null.</li>
		 * </ul>
		 */
		FAILED
	}

	Job getJob();

	UUID getUUID();

	Status getStatus();

	Instant getCreationTime();

	Instant getStartTime();

	Instant getFinishTime();

	/**
	 * Get the UUID of the agent that provided this result. The agent may or may not still exist.
	 *
	 * @return The UUID of the agent that provided this result.
	 */
	UUID getAgentUUID();

	static String statusToString(Status status) {
		switch(status) {
			case NOT_RUN:
				return "NOT_RUN";
			case RUNNING:
				return "RUNNING";
			case COMPLETED:
				return "COMPLETED";
			case FAILED:
				return "FAILED";
		}

		throw new IllegalArgumentException();
	}

	static Status stringToStatus(String s) {
		switch(s) {
			case "NOT_RUN":
				return Status.NOT_RUN;
			case "RUNNING":
				return Status.RUNNING;
			case "COMPLETED":
				return Status.COMPLETED;
			case "FAILED":
				return Status.FAILED;
		}

		throw new IllegalArgumentException();
	}
}
