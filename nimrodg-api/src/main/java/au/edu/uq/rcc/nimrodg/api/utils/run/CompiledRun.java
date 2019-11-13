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
package au.edu.uq.rcc.nimrodg.api.utils.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CompiledRun {

	public final int numVariables;
	public final int numJobs;
	public final int numTasks;
	public final List<CompiledVariable> variables; // Variables are in index-ascending order
	public final List<CompiledJob> jobs; // Jobs are in index-ascending order
	public final List<CompiledTask> tasks;

	CompiledRun(List<CompiledVariable> vars, List<CompiledJob> jobs, List<CompiledTask> tasks) {
		this.numVariables = vars.size();
		this.numJobs = jobs.size();
		this.numTasks = tasks.size();
		this.variables = List.copyOf(vars);
		this.jobs = List.copyOf(jobs);
		this.tasks = List.copyOf(tasks);
	}

	public List<Map<String, String>> buildJobsList() {
		List<Map<String, String>> jobsList = new ArrayList<>(this.jobs.size());
		this.jobs.forEach(j -> {
			Map<String, String> job = new HashMap<>();
			for(int i = 0; i < j.indices.length; ++i) {
				job.put(this.variables.get(i).name, this.variables.get(i).values.get(j.indices[i]));
			}
			jobsList.add(job);
		});
		return jobsList;
	}
}
