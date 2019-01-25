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

import au.edu.uq.rcc.nimrodg.api.Task;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RunBuilder {

	private static final Set<String> IMPLICIT_VARIABLES;

	static {
		IMPLICIT_VARIABLES = new HashSet<>();
		IMPLICIT_VARIABLES.add("jobname");
		IMPLICIT_VARIABLES.add("jobindex");
	}

	private final List<VariableBuilder> m_Variables;
	private final List<JobBuilder> m_Jobs;
	private final List<CompiledTask> m_Tasks;
	private final List<ParameterBuilder> m_Parameters;

	public RunBuilder() {
		m_Variables = new ArrayList<>();
		m_Jobs = new ArrayList<>();
		m_Tasks = new ArrayList<>();
		m_Parameters = new ArrayList<>();
	}

	public RunBuilder addVariable(VariableBuilder var) {
		m_Variables.add(new VariableBuilder(var));
		return this;
	}

	public RunBuilder addVariables(Collection<VariableBuilder> vars) {
		vars.stream().map(var -> new VariableBuilder(var)).forEach(m_Variables::add);
		return this;
	}

	public RunBuilder addParameter(ParameterBuilder param) {
		m_Parameters.add(new ParameterBuilder(param));
		return this;
	}

	public RunBuilder addParameters(Collection<ParameterBuilder> params) {
		params.stream().map(p -> new ParameterBuilder(p)).forEach(m_Parameters::add);
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

	public static class RunfileBuildException extends Exception {

		public RunfileBuildException(String s) {
			super(s);
		}
	}

	public static class FirstVariableIndexNonzero extends RunfileBuildException {

		public FirstVariableIndexNonzero(CompiledVariable var) {
			super(String.format("First variable '%s' has nonzero index.", var.name));
		}

	}

	public static class DuplicateVariableException extends RunfileBuildException {

		public DuplicateVariableException(String name, int count) {
			super(String.format("Duplicate variable '%s', %d instances", name, count));
		}
	}

	public static class DuplicateVariableIndexException extends RunfileBuildException {

		public DuplicateVariableIndexException(CompiledVariable v1, CompiledVariable v2) {
			super(String.format("Variables '%s' and '%s' share index %d", v1.name, v2.name, v1.index));
		}
	}

	public static class NonConsecutiveVariableIndexException extends RunfileBuildException {

		public NonConsecutiveVariableIndexException(CompiledVariable v1, CompiledVariable v2) {
			super(String.format("Variable '%s' (%d) has partner '%s' (%d) with non-consecutive index.", v1.name, v1.index, v2.name, v2.index));
		}

	}

	public static class FirstJobIndexNononeException extends RunfileBuildException {

		public FirstJobIndexNononeException(CompiledJob job) {
			super(String.format("First job has non-one index '%d'", job.index));
		}

	}

	public static class DuplicateJobIndexException extends RunfileBuildException {

		public DuplicateJobIndexException(int index) {
			super(String.format("Multiple jobs with same index %d", index));
		}

	}

	public static class NonConsecutiveJobIndexException extends RunfileBuildException {

		public NonConsecutiveJobIndexException(int i1, int i2) {
			super(String.format("Job indices '%d' and '%d' are nonconsecutive", i1, i2));
		}

	}

	public static class InvalidJobVariablesException extends RunfileBuildException {

		public InvalidJobVariablesException(CompiledJob j) {
			super(String.format("Job '%d' has missing or too many variable references", j.index));
		}

	}

	public static class InvalidJobVariableIndexException extends RunfileBuildException {

		public InvalidJobVariableIndexException(CompiledJob j, CompiledVariable var, int index) {
			super(String.format("Job '%d' references invalid value index '%d' in variable '%s'", j.index, index, var.name));
		}

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
				throw new DuplicateVariableException(name, num);
			}
		}

		if(!vars.isEmpty()) {
			/* Sort them by index and check if the indices are consecutive */
			vars.sort((v1, v2) -> Integer.compare(v1.index, v2.index));

			if(vars.get(0).index != 0) {
				throw new FirstVariableIndexNonzero(vars.get(0));
			}

			for(int i = 1; i < vars.size(); ++i) {
				CompiledVariable v1 = vars.get(i - 1);
				CompiledVariable v2 = vars.get(i);

				if(v1.index == v2.index) {
					throw new DuplicateVariableIndexException(v1, v2);
				} else if(v1.index != v2.index - 1) {
					throw new NonConsecutiveVariableIndexException(v1, v2);
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
		initialJobs.sort((j1, j2) -> Integer.compare(j1.index, j2.index));

		if(!initialJobs.isEmpty() && initialJobs.get(0).index != 1) {
			throw new FirstJobIndexNononeException(initialJobs.get(0));
		}

		for(int i = 1; i < initialJobs.size(); ++i) {
			CompiledJob j1 = initialJobs.get(i - 1);
			CompiledJob j2 = initialJobs.get(i);

			if(j1.index == j2.index) {
				throw new DuplicateJobIndexException(j1.index);
			} else if(j1.index != j2.index - 1) {
				throw new NonConsecutiveJobIndexException(j1.index, j2.index);
			}
		}

		/* Validate all the value indices */
		for(CompiledJob j : initialJobs) {
			if(j.indices.length != vars.size()) {
				throw new InvalidJobVariablesException(j);
			}

			for(int i = 0; i < j.indices.length; ++i) {

				/* m_Variables is sorted and validated by this stage, so this is safe. */
				CompiledVariable var = vars.get(i);
				int index = j.indices[i];
				if(index < 0 || index >= var.values.size()) {
					throw new InvalidJobVariableIndexException(j, var, index);
				}
			}
		}

		return initialJobs;
	}

	public static class DuplicateTaskNameException extends RunfileBuildException {

		public DuplicateTaskNameException(Task.Name name) {
			super(String.format("Duplicate task name %s", name.toString().toLowerCase()));
		}
	}

	public static class MissingMainTaskException extends RunfileBuildException {

		public MissingMainTaskException() {
			super("Missing main task");
		}
	}

	public static class NonMainHasSubstitutions extends RunfileBuildException {

		public NonMainHasSubstitutions() {
			super("Non main task is not allowed to have substitutions");
		}

	}

	public static class InvalidVariableSubstitutionReferenceException extends RunfileBuildException {

		public InvalidVariableSubstitutionReferenceException(String var) {
			super(String.format("Invalid variable reference '%s' in substitution", var));
		}
	}

	public static class ImplicitVariableConflict extends RunfileBuildException {

		public ImplicitVariableConflict() {
			super("Variable name conflicts with an implicit variable");
		}
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
				throw new DuplicateTaskNameException(name);
			}
		}

		/* Check we actually have a main task. */
		if(mainTask == null) {
			throw new MissingMainTaskException();
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
						if(!((CompiledCopyCommand)ccmd).destPath.substitutions.isEmpty()) {
							throw new NonMainHasSubstitutions();
						}

						if(!((CompiledCopyCommand)ccmd).sourcePath.substitutions.isEmpty()) {
							throw new NonMainHasSubstitutions();
						}
						break;
					case Exec:
						for(CompiledArgument arg : ((CompiledExecCommand)ccmd).arguments) {
							if(!arg.substitutions.isEmpty()) {
								throw new NonMainHasSubstitutions();
							}
						}
						break;
				}
			}
		}

		/* Check substitutions. */
		Set<String> subVarNames = mainTask.commands.stream()
				.flatMap(cmd -> cmd.normalise().stream()
				.flatMap(arg -> arg.substitutions
				.stream())).map(s -> s.variable()).collect(Collectors.toSet());

		subVarNames.removeAll(varNames);

		if(!subVarNames.isEmpty()) {
			throw new InvalidVariableSubstitutionReferenceException(subVarNames.stream().findFirst().get());
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
				for(int i = 0; i < var.values.size(); ++i) {
					List<Integer> n = new ArrayList<>(c);
					n.add(i);
					nn.add(n);
				}
			}
			combinations = nn;
		}
		return combinations;
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
			throw new ImplicitVariableConflict();
		}

		/* Check our supplied initial jobs are valid. */
		checkJobs(vars, m_Jobs.stream().map(j -> j.build()).collect(Collectors.toList()));

		/* Apply the parameters to the jobs. */
		List<CompiledJob> jobs = applyParameters(pars, m_Jobs);

		/* Now the the parameters have been processed, combine them with the variables. */
		checkJobs(allVars, jobs);
		checkTasks(varNames);

		return new CompiledRun(allVars, jobs, m_Tasks);
	}

}
