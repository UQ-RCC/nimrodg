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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobSpecification;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TempExperiment {

	public final long id;
	public final String name;
	public final String workDir;
	public final Experiment.State state;
	public final Instant created;
	public final String path;
	public final Set<String> variables;

	/* TODO: These are rarely used, if ever. It might be worth getting them on-demand once and caching it,
	 * as it's expensive to generate DB-side.
	 */
	public final Map<Task.Name, CompiledTask> tasks;

	public TempExperiment(long id, String name, String workDir, Experiment.State state, Instant created, String path, Collection<String> variables, Collection<CompiledTask> tasks) {
		this.id = id;
		this.name = name;
		this.workDir = workDir;
		this.state = state;
		this.created = created;
		this.path = path;
		this.variables = Set.copyOf(variables);
		this.tasks = Map.copyOf(tasks.stream().collect(Collectors.toMap(ct -> ct.name, ct -> ct)));
	}

	public Impl create(NimrodDBAPI db) {
		return new Impl(db);
	}

	public class Impl implements Experiment {

		private final NimrodDBAPI db;
		public final TempExperiment base;

		private Impl(NimrodDBAPI db) {
			this.db = db;
			this.base = TempExperiment.this;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public State getState() {
			return db.runSQL(() -> db.getTempExp(id)).map(e -> e.state).orElseThrow(IllegalStateException::new);
		}

		@Override
		public String getWorkingDirectory() {
			return workDir;
		}

		@Override
		public Instant getCreationTime() {
			return created;
		}

		@Override
		public Set<String> getVariables() {
			return variables;
		}

		@Override
		public JobSpecification getJobSpecification() {
			return JobSpecification.empty();
		}

		@Override
		public Map<Task.Name, Task> getTasks() {
			return Collections.unmodifiableMap(tasks);
		}

		@Override
		public Task getTask(Task.Name name) {
			return base.tasks.get(name);
		}

		@Override
		public boolean isPersistent() {
			return getState() == State.PERSISTENT;
		}

		@Override
		public boolean isActive() {
			return getState() != State.STOPPED;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof Impl)) {
				return false;
			}

			return id == ((Impl)obj).base.id;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}
	}
}
