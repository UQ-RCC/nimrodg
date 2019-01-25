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
import au.edu.uq.rcc.nimrodg.api.Task;

public final class CopyCommandImpl implements CopyCommand {

	private final CommandImpl commandImpl;
	private final Task task;
	private final CopyCommand.Context sourceCtx;
	private final CommandArgumentImpl sourcePath;
	private final CopyCommand.Context destCtx;
	private final CommandArgumentImpl destPath;

	CopyCommandImpl(CommandImpl cmd, Task task, CopyCommand.Context srcCtx, CommandArgumentImpl srcPath, CopyCommand.Context dstCtx, CommandArgumentImpl dstPath) {
		this.commandImpl = cmd;
		this.task = task;
		this.sourceCtx = srcCtx;
		this.sourcePath = srcPath;
		this.destCtx = dstCtx;
		this.destPath = dstPath;
	}

	public CommandImpl getCommand() {
		return commandImpl;
	}

	@Override
	public CopyCommand.Context getSourceContext() {
		return sourceCtx;
	}

	@Override
	public CommandArgumentImpl getSourcePath() {
		return sourcePath;
	}

	@Override
	public CopyCommand.Context getDestinationContext() {
		return destCtx;
	}

	@Override
	public CommandArgumentImpl getDestinationPath() {
		return destPath;
	}

	@Override
	public Task getTask() {
		return task;
	}

	@Override
	public Command.Type getType() {
		return Command.Type.Copy;
	}

}
