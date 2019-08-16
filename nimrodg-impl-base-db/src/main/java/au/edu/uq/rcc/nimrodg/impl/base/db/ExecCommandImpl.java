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

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.ExecCommand;
import au.edu.uq.rcc.nimrodg.api.Task;
import java.util.Collections;
import java.util.List;

public final class ExecCommandImpl implements ExecCommand {

	private final CommandImpl commandImpl;
	private final Task task;
	private final boolean searchPath;
	private final CommandArgument program;
	private final List<CommandArgumentImpl> arguments;

	ExecCommandImpl(CommandImpl cmd, Task task, boolean searchPath, CommandArgument program, List<CommandArgumentImpl> args) {
		this.commandImpl = cmd;
		this.task = task;
		this.searchPath = searchPath;
		this.program = program;
		this.arguments = args;
	}

	public CommandImpl getCommand() {
		return commandImpl;
	}

	@Override
	public boolean searchPath() {
		return searchPath;
	}

	@Override
	public CommandArgument getProgram() {
		return program;
	}

	@Override
	public List<CommandArgument> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	@Override
	public Task getTask() {
		return task;
	}

	@Override
	public Command.Type getType() {
		return Command.Type.Exec;
	}
}
