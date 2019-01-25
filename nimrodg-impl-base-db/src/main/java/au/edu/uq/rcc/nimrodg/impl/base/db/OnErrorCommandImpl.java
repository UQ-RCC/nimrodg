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
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.Task;

public final class OnErrorCommandImpl implements OnErrorCommand {

	private final CommandImpl command;
	private final Task task;
	private final OnErrorCommand.Action action;

	public OnErrorCommandImpl(CommandImpl cmd, Task task, OnErrorCommand.Action action) {
		this.command = cmd;
		this.task = task;
		this.action = action;
	}

	public CommandImpl getCommand() {
		return command;
	}

	@Override
	public OnErrorCommand.Action getAction() {
		return action;
	}

	@Override
	public Task getTask() {
		return task;
	}

	@Override
	public Command.Type getType() {
		return Command.Type.OnError;
	}

}
