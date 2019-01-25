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
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import java.util.ArrayList;
import java.util.List;
import static java.util.stream.Collectors.toList;

public final class CommandImpl {

	private CommandImpl(TaskImpl task, int index, Command.Type type) {
		this.task = task;
		this.index = index;
		this.type = type;
		this.args = new ArrayList<>();
		this.command = null;
	}

	private Command generateAPICommand() {
		/* Build the API version of the command */
		switch(type) {
			case OnError:
				return new OnErrorCommandImpl(this, task, OnErrorCommand.stringToAction(args.get(0).getText()));
			case Redirect:
				return new RedirectCommandImpl(
						this,
						task,
						RedirectCommand.stringToStream(args.get(0).getText()),
						Boolean.parseBoolean(args.get(1).getText()),
						args.get(2)
				);
			case Copy:
				return new CopyCommandImpl(
						this,
						task,
						CopyCommand.stringToContext(args.get(0).getText()),
						args.get(1),
						CopyCommand.stringToContext(args.get(2).getText()),
						args.get(3)
				);
			case Exec:
				return new ExecCommandImpl(
						this,
						task,
						Boolean.parseBoolean(args.get(0).getText()),
						args.get(1),
						args.stream().skip(2).collect(toList())
				);
		}

		throw new IllegalStateException();
	}

	private final int index;
	private final TaskImpl task;
	private final Command.Type type;
	private final List<CommandArgumentImpl> args;
	private transient Command command;

	public Command getCommand() {
		if(command == null) {
			command = generateAPICommand();
		}

		return command;
	}

	public TaskImpl getTask() {
		return task;
	}

	public Command.Type getType() {
		return type;
	}

	static CommandImpl create(TaskImpl task, int index, CompiledCommand ccmd) {
		CommandImpl cmd = new CommandImpl(task, index, ccmd.type);
		ccmd.normalise().forEach(arg -> cmd.args.add(CommandArgumentImpl.create(cmd, cmd.args.size() + 1, arg)));
		return cmd;
	}
}
