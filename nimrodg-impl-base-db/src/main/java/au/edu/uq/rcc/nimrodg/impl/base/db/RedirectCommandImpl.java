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

import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.Task;

public class RedirectCommandImpl implements RedirectCommand {

	private final CommandImpl commandImpl;
	private final Task task;
	private final Stream stream;
	private final boolean append;
	private final CommandArgumentImpl file;

	public RedirectCommandImpl(CommandImpl commandImpl, Task task, Stream stream, boolean append, CommandArgumentImpl file) {
		this.commandImpl = commandImpl;
		this.task = task;
		this.stream = stream;
		this.append = append;
		this.file = file;
	}

	public CommandImpl getCommand() {
		return commandImpl;
	}

	@Override
	public Stream getStream() {
		return stream;
	}

	@Override
	public boolean getAppend() {
		return append;
	}

	@Override
	public CommandArgumentImpl getFile() {
		return file;
	}

	@Override
	public Task getTask() {
		return task;
	}

	@Override
	public Type getType() {
		return Type.Redirect;
	}

}
