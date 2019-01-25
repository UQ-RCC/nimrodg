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
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import java.util.ArrayList;
import java.util.List;
import static java.util.stream.Collectors.toList;

public final class TaskImpl implements Task {

	TaskImpl(TempExperiment.Impl e, CompiledTask ct) {
		exp = e;
		name = ct.name;
		commands = new ArrayList<>();
		int i = 0;
		for(CompiledCommand cmd : ct.commands) {
			commands.add(CommandImpl.create(this, i, cmd));
			++i;
		}
	}

	private final TempExperiment.Impl exp;
	private final Task.Name name;
	private final List<CommandImpl> commands;

	@Override
	public TempExperiment.Impl getExperiment() {
		return exp;
	}

	@Override
	public Task.Name getName() {
		return name;
	}

	@Override
	public List<Command> getCommands() {
		return commands.stream().map(cmd -> cmd.getCommand()).collect(toList());
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 71 * hash + name.hashCode();
		hash = 71 * hash + exp.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof TaskImpl)) {
			return false;
		}

		TaskImpl task = (TaskImpl)obj;
		return name == task.name && exp.equals(task.exp);
	}
}
