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
package au.edu.uq.rcc.nimrodg.api.utils;

import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledArgument;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCopyCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledOnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledExecCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledJob;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledVariable;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.ExecCommand;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRedirectCommand;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MsgUtils {

	public static NetworkJob resolveNonSubstitutionTask(UUID uuid, Task task, URI txuri) throws NimrodException {
		return new NetworkJob(uuid, -1, txuri.toString(), buildCommandList(task, new HashMap<>()), new HashMap<>());
	}

	private static Map<String, String> buildEnvironment(String expName, UUID jobUuid, long jobIndex, String txuri, Map<String, String> vars) {
		Map<String, String> env = new HashMap<>();
		env.put("NIMROD_EXPNAME", expName);
		env.put("NIMROD_JOBUUID", jobUuid.toString());
		env.put("NIMROD_JOBINDEX", Long.toString(jobIndex));
		env.put("NIMROD_TXURI", txuri);
		vars.forEach((k, v) -> env.put(String.format("NIMROD_VAR_%s", k), v));
		return env;
	}

	/**
	 * Resolve a given job and {@link Task} from the Nimrod API into a structure that can be transmitted over the
	 * network.
	 *
	 * @param uuid The UUID of the job.
	 * @param job The job instance.
	 * @param task The name of the task.
	 * @param txuri
	 * @return A resolved job, ready for sending.
	 */
	public static NetworkJob resolveJob(UUID uuid, Job job, Task.Name task, URI txuri) throws IllegalArgumentException {
		Experiment exp = job.getExperiment();

		Task ct = exp.getTask(task);
		if(ct == null) {
			throw new IllegalArgumentException("Invalid task name");
		}

		/* Build the environment variables and substitutions. */
		Map<String, String> varMap = job.getVariables();

		Map<String, String> env = buildEnvironment(
				exp.getName(),
				uuid,
				job.getIndex(),
				txuri.toString(),
				varMap
		);

		return new NetworkJob(uuid, job.getIndex(), txuri.toString(), buildCommandList(ct, varMap), env);
	}

	private static List<NetworkJob.ResolvedCommand> buildCommandList(Task ct, Map<String, String> subs) {
		List<NetworkJob.ResolvedCommand> commands = new ArrayList<>();
		for(Command command : ct.getCommands()) {
			switch(command.getType()) {
				case OnError: {
					commands.add(new NetworkJob.OnErrorCommand(((OnErrorCommand)command).getAction()));
					break;
				}
				case Redirect: {
					RedirectCommand ccmd = (RedirectCommand)command;
					commands.add(new NetworkJob.RedirectCommand(
							ccmd.getStream(),
							ccmd.getAppend(),
							resolveArgument(ccmd.getFile(), subs)
					));
					break;
				}
				case Copy: {
					CopyCommand ccmd = (CopyCommand)command;
					commands.add(new NetworkJob.CopyCommand(
							ccmd.getSourceContext(),
							resolveArgument(ccmd.getSourcePath(), subs),
							ccmd.getDestinationContext(),
							resolveArgument(ccmd.getDestinationPath(), subs)
					));
					break;
				}
				case Exec: {
					ExecCommand ccmd = (ExecCommand)command;
					List<String> arguments = new ArrayList<>(ccmd.getArguments().size());
					ccmd.getArguments().forEach(arg -> arguments.add(resolveArgument(arg, subs)));
					commands.add(new NetworkJob.ExecCommand(ccmd.getProgram(), arguments, ccmd.searchPath()));
					break;
				}
			}
		}

		return commands;
	}

	/**
	 * Resolve a given job and {@link Task} from a {@link CompiledRun} into a structure that can be transmitted over the
	 * network.
	 *
	 * @param uuid The UUID of the job.
	 * @param r The compiled run. May not be null.
	 * @param index The index of the job. Must be &gt;= 1.
	 * @param task The name of the task.
	 * @param txuri The transfer URI.
	 * @param expName
	 * @return A resolved job, ready for sending.
	 */
	public static NetworkJob resolveJob(UUID uuid, CompiledRun r, int index, Task.Name task, String txuri, String expName) throws IllegalArgumentException {
		CompiledJob cj = null;
		for(CompiledJob j : r.jobs) {
			if(j.index == index) {
				cj = j;
				break;
			}
		}
		if(cj == null) {
			throw new IllegalArgumentException("Invalid task index");
		}

		CompiledTask ct = null;
		for(CompiledTask t : r.tasks) {
			if(t.name == task) {
				ct = t;
				break;
			}
		}
		if(ct == null) {
			throw new IllegalArgumentException("Invalid task name");
		}

		/* Build the environment variables and substitutions. */
		Map<String, String> varMap = new HashMap<>();
		int i = 0;
		for(CompiledVariable var : r.variables) {
			String vval = var.supplier.getAt(cj.indices[i]);
			varMap.put(var.name, vval);
			++i;
		}

		Map<String, String> env = buildEnvironment(
				expName,
				uuid,
				index,
				txuri,
				varMap
		);

		return new NetworkJob(uuid, cj.index, txuri, buildCommandList(ct, varMap), env);
	}

	private static List<NetworkJob.ResolvedCommand> buildCommandList(CompiledTask ct, Map<String, String> subs) {
		List<NetworkJob.ResolvedCommand> commands = new ArrayList<>();
		for(CompiledCommand command : ct.commands) {
			switch(command.type) {
				case OnError: {
					commands.add(new NetworkJob.OnErrorCommand(((CompiledOnErrorCommand)command).action));
					break;
				}
				case Redirect: {
					CompiledRedirectCommand ccmd = (CompiledRedirectCommand)command;
					commands.add(new NetworkJob.RedirectCommand(
							ccmd.stream,
							ccmd.append,
							resolveArgument(ccmd.file, subs)
					));
					break;
				}
				case Copy: {
					CompiledCopyCommand ccmd = (CompiledCopyCommand)command;
					commands.add(new NetworkJob.CopyCommand(
							ccmd.sourceContext,
							resolveArgument(ccmd.sourcePath, subs),
							ccmd.destContext,
							resolveArgument(ccmd.destPath, subs)
					));
					break;
				}
				case Exec: {
					CompiledExecCommand ccmd = (CompiledExecCommand)command;
					List<String> arguments = new ArrayList<>(ccmd.arguments.size());

					ccmd.arguments.forEach(arg -> arguments.add(resolveArgument(arg, subs)));
					commands.add(new NetworkJob.ExecCommand(ccmd.program, arguments, ccmd.searchPath));
					break;
				}
			}
		}

		return commands;
	}

	private static String resolveArgument(CompiledArgument arg, Map<String, String> subs) {
		return StringUtils.applySubstitutions(arg.getText(), arg.getSubstitutions(), subs);
	}

	private static String resolveArgument(CommandArgument arg, Map<String, String> subs) {
		return StringUtils.applySubstitutions(arg.getText(), arg.getSubstitutions(), subs);
	}

	private static CompiledSubstitution toSubstitution(au.edu.uq.rcc.nimrodg.api.Substitution s) {
		return new CompiledSubstitution(s.getVariable(), s.getStartIndex(), s.getEndIndex(), s.getRelativeStartIndex());
	}
}
