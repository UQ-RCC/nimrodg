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

import au.edu.uq.rcc.nimrodg.api.Substitution;
import au.edu.uq.rcc.nimrodg.api.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RunBuilder {

	private static final Set<String> IMPLICIT_VARIABLES = Set.of("jobname", "jobindex");

	private final List<VariableBuilder> m_Variables;
	private final List<JobBuilder> m_Jobs;
	private final List<CompiledTask> m_Tasks;

	public RunBuilder() {
		m_Variables = new ArrayList<>();
		m_Jobs = new ArrayList<>();
		m_Tasks = new ArrayList<>();
	}

	public RunBuilder addVariable(VariableBuilder var) {
		m_Variables.add(new VariableBuilder(var));
		return this;
	}

	public RunBuilder addVariables(Collection<VariableBuilder> vars) {
		vars.stream().map(VariableBuilder::new).forEach(m_Variables::add);
		return this;
	}

	public RunBuilder addJob(JobBuilder job) {
		m_Jobs.add(job);
		return this;
	}

	public RunBuilder addJobs(Collection<JobBuilder> jobs) {
		m_Jobs.addAll(jobs);
		return this;
	}

	public RunBuilder addTask(CompiledTask task) {
		m_Tasks.add(task);
		return this;
	}

	public RunBuilder addTasks(Collection<CompiledTask> tasks) {
		m_Tasks.addAll(tasks);
		return this;
	}

	private void checkAndProcessVariables(List<VariableBuilder> inVars, List<CompiledVariable> vars, List<CompiledVariable> pars) throws RunfileBuildException {
		List<VariableBuilder> parameterBuilders = new ArrayList<>();
		HashMap<String, Integer> varNames = new HashMap<>();

		vars.clear();
		/* Filter the variables from the parameters and check for duplicate names. */
		for(VariableBuilder vb : inVars) {
			CompiledVariable v = vb.build();
			if(v.index < 0) {
				parameterBuilders.add(vb);
			} else {
				vars.add(v);
			}

			varNames.put(v.name, varNames.getOrDefault(v.name, 0) + 1);
		}

		for(String name : varNames.keySet()) {
			int num = varNames.get(name);
			if(num != 1) {
				throw new RunfileBuildException.DuplicateVariable(name, num);
			}
		}

		if(!vars.isEmpty()) {
			/* Sort them by index and check if the indices are consecutive */
			vars.sort(Comparator.comparingInt(v -> v.index));

			if(vars.get(0).index != 0) {
				throw new RunfileBuildException.FirstVariableIndexNonZero(vars.get(0));
			}

			for(int i = 1; i < vars.size(); ++i) {
				CompiledVariable v1 = vars.get(i - 1);
				CompiledVariable v2 = vars.get(i);

				if(v1.index == v2.index) {
					throw new RunfileBuildException.DuplicateVariableIndex(v1, v2);
				} else if(v1.index != v2.index - 1) {
					throw new RunfileBuildException.NonConsecutiveVariableIndex(v1, v2);
				}
			}
		}

		pars.clear();
		/* Now go and patch the parameters. */
		for(int i = 0; i < parameterBuilders.size(); ++i) {
			pars.add(parameterBuilders.get(i).index(vars.size() + i).build());
		}
	}

	private static List<CompiledJob> checkJobs(List<CompiledVariable> vars, List<CompiledJob> runJobs) throws RunfileBuildException {
		List<CompiledJob> initialJobs = new ArrayList<>(runJobs);
		initialJobs.sort(Comparator.comparingInt(j -> j.index));

		if(!initialJobs.isEmpty() && initialJobs.get(0).index != 1) {
			throw new RunfileBuildException.FirstJobIndexNonOne(initialJobs.get(0));
		}

		for(int i = 1; i < initialJobs.size(); ++i) {
			CompiledJob j1 = initialJobs.get(i - 1);
			CompiledJob j2 = initialJobs.get(i);

			if(j1.index == j2.index) {
				throw new RunfileBuildException.DuplicateJobIndex(j1.index);
			} else if(j1.index != j2.index - 1) {
				throw new RunfileBuildException.NonConsecutiveJobIndex(j1.index, j2.index);
			}
		}

		/* Validate all the value indices */
		for(CompiledJob j : initialJobs) {
			if(j.indices.length != vars.size()) {
				throw new RunfileBuildException.InvalidJobVariables(j);
			}

			for(int i = 0; i < j.indices.length; ++i) {

				/* m_Variables is sorted and validated by this stage, so this is safe. */
				CompiledVariable var = vars.get(i);
				int index = j.indices[i];
				if(index < 0 || index >= var.supplier.getTotalCount()) {
					throw new RunfileBuildException.InvalidJobVariableIndex(j, var, index);
				}
			}
		}

		return initialJobs;
	}

	private void checkTasks(Set<String> varNames) throws RunfileBuildException {
		HashMap<Task.Name, Integer> nameCounts = new HashMap<>();
		CompiledTask mainTask = null;
		for(CompiledTask t : m_Tasks) {
			if(t.name == Task.Name.Main) {
				mainTask = t;
			}
			nameCounts.put(t.name, nameCounts.getOrDefault(t.name, 0) + 1);
		}

		for(Task.Name name : nameCounts.keySet()) {

			int count = nameCounts.get(name);
			if(count > 1) {
				throw new RunfileBuildException.DuplicateTaskName(name);
			}
		}

		/* Check we actually have a main task. */
		if(mainTask == null) {
			throw new RunfileBuildException.MissingMainTask();
		}

		/* Ensure that only the Main task has substitutions. */
		for(CompiledTask t : m_Tasks) {
			if(t.name == Task.Name.Main) {
				mainTask = t;
				continue;
			}

			for(CompiledCommand ccmd : t.commands) {
				switch(ccmd.type) {
					case OnError:
						continue;
					case Copy:
						if(!((CompiledCopyCommand)ccmd).destPath.getSubstitutions().isEmpty()) {
							throw new RunfileBuildException.NonMainHasSubstitutions();
						}

						if(!((CompiledCopyCommand)ccmd).sourcePath.getSubstitutions().isEmpty()) {
							throw new RunfileBuildException.NonMainHasSubstitutions();
						}
						break;
					case Exec:
						for(CompiledArgument arg : ((CompiledExecCommand)ccmd).arguments) {
							if(!arg.getSubstitutions().isEmpty()) {
								throw new RunfileBuildException.NonMainHasSubstitutions();
							}
						}
						break;
				}
			}
		}

		/* Check substitutions. */
		Set<String> subVarNames = mainTask.commands.stream()
				.flatMap(cmd -> cmd.normalise().stream()
				.flatMap(arg -> arg.getSubstitutions()
				.stream())).map(Substitution::getVariable).collect(Collectors.toSet());

		subVarNames.removeAll(varNames);

		if(!subVarNames.isEmpty()) {
			throw new RunfileBuildException.InvalidVariableSubstitutionReference(subVarNames.stream().findFirst().get());
		}

	}

	private static List<CompiledJob> applyParameters(List<CompiledVariable> parms, List<JobBuilder> jobBuilders) {
		/* Catch a special case */
		if(parms.isEmpty() && jobBuilders.isEmpty()) {
			return new ArrayList<>();
		}

		/*
		 * We have parameterse, so take the cartesian product of the values then apply each one to the
		 * the existing jobs (if any).
		 *
		 * This could get very big, so this might have to be reworked to stream them into
		 * the database or something.
		 *
		 * NB: This will contain a single empty list if parms is empty.
		 */
		List<List<Integer>> cpi = cartesianProduct(parms);

		List<CompiledJob> jobs = new ArrayList<>();
		int index = 1;

		List<JobBuilder> builders = new ArrayList<>(jobBuilders);
		if(builders.isEmpty()) {
			builders.add(new JobBuilder());
		}

		/* Let's make it even bigger! */
		for(JobBuilder bj : builders) {
			for(List<Integer> set : cpi) {
				JobBuilder jb = new JobBuilder(bj);
				jb.index(index++);
				jb.addIndices(set);
				jobs.add(jb.build());
			}
		}
		return jobs;
	}

	public static List<List<Integer>> cartesianProduct(List<CompiledVariable> vars) {
		/* Start with an empty combination */
		List<List<Integer>> combinations = new ArrayList<>();
		combinations.add(new ArrayList<>());
		for(CompiledVariable var : vars) {
			List<List<Integer>> nn = new ArrayList<>();
			for(List<Integer> c : combinations) {
				for(int i = 0; i < var.supplier.getTotalCount(); ++i) {
					List<Integer> n = new ArrayList<>(c);
					n.add(i);
					nn.add(n);
				}
			}
			combinations = nn;
		}
		return combinations;
	}

	/**
	 * Get the estimated number of jobs this experiment will contain.
	 * This does not take into account the validity of the job or variable invariants.
	 *
	 * This will always be greater than or equal to the actual job count.
	 *
	 * @return The estimated number of jobs this experiment will contain.
	 */
	public int getEstimatedJobCount() {
		int count = 1;

		for(VariableBuilder vb : m_Variables) {
			count *= vb.build().supplier.getTotalCount();
		}

		if(!m_Jobs.isEmpty()) {
			count *= m_Jobs.size();
		}
		return count;
	}

	public CompiledRun build() throws RunfileBuildException {
		/* Check everything's good! */
		List<CompiledVariable> vars = new ArrayList<>();
		List<CompiledVariable> pars = new ArrayList<>();
		checkAndProcessVariables(m_Variables, vars, pars);

		List<CompiledVariable> allVars = new ArrayList<>(vars.size() + pars.size());
		allVars.addAll(vars);
		allVars.addAll(pars);
		Set<String> varNames = allVars.stream().map(v -> v.name).collect(Collectors.toSet());
		varNames.addAll(IMPLICIT_VARIABLES);
		if(varNames.size() != allVars.size() + IMPLICIT_VARIABLES.size()) {
			throw new RunfileBuildException.ImplicitVariableConflict();
		}

		/* Check our supplied initial jobs are valid. */
		checkJobs(vars, m_Jobs.stream().map(JobBuilder::build).collect(Collectors.toList()));

		/* Apply the parameters to the jobs. */
		List<CompiledJob> jobs = applyParameters(pars, m_Jobs);

		/* Now the the parameters have been processed, combine them with the variables. */
		checkJobs(allVars, jobs);
		checkTasks(varNames);

		assert this.getEstimatedJobCount() >= jobs.size();
		return new CompiledRun(allVars, jobs, m_Tasks);
	}

}
