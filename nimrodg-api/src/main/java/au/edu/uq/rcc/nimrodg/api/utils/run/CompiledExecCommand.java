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

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.ExecCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static java.util.stream.Collectors.toList;

public final class CompiledExecCommand extends CompiledCommand implements ExecCommand {

	/**
	 * The name of the program to execute. If null, use the system's default shell.
	 *
	 * i.e. NULL IF SHEXEC
	 */
	public final String program;

	/**
	 * The list of arguments (including argv[0]). If program is null, this will only contain the command string to
	 * execute in the shell.
	 */
	public final List<CompiledArgument> arguments;

	/**
	 * Should the system's path be searched?
	 */
	public final boolean searchPath;

	CompiledExecCommand(String program, List<CompiledArgument> args, boolean searchPath) {
		super(Command.Type.Exec);
		this.program = program;
		this.arguments = List.copyOf(args);
		this.searchPath = searchPath;
	}

	@Override
	public boolean searchPath() {
		return searchPath;
	}

	@Override
	public String getProgram() {
		return program;
	}

	@Override
	public List<CommandArgument> getArguments() {
		return Collections.unmodifiableList(arguments);
	}

	public CompiledArgument programAsArgument() {
		return new CompiledArgument(program);
	}

	public CompiledArgument searchPathAsArgument() {
		return new CompiledArgument(Boolean.toString(searchPath));
	}

	@Override
	public List<CompiledArgument> normalise() {
		List<CompiledArgument> a = new ArrayList<>();
		a.add(this.searchPathAsArgument());
		a.add(this.programAsArgument());
		a.addAll(this.arguments);
		return a;
	}

	public static CompiledExecCommand resolveArguments(List<CompiledArgument> args) throws IllegalArgumentException {
		if(args.size() < 2) {
			throw new IllegalArgumentException();
		}

		return new CompiledExecCommand(
				args.get(1).getText(),
				args.stream().skip(2).collect(toList()),
				Boolean.parseBoolean(args.get(0).getText())
		);
	}
}
