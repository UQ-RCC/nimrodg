/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.CopyCommand.Context;
import java.util.List;

public final class CompiledCopyCommand extends CompiledCommand implements CopyCommand {

	public final Context sourceContext;
	public final CompiledArgument sourcePath;

	public final Context destContext;
	public final CompiledArgument destPath;

	CompiledCopyCommand(Context srcCtx, CompiledArgument srcPath, Context dstCtx, CompiledArgument dstPath) {
		super(Command.Type.Copy);
		this.sourceContext = srcCtx;
		this.sourcePath = srcPath;
		this.destContext = dstCtx;
		this.destPath = dstPath;
	}

	@Override
	public Context getSourceContext() {
		return sourceContext;
	}

	@Override
	public CommandArgument getSourcePath() {
		return sourcePath;
	}

	@Override
	public Context getDestinationContext() {
		return destContext;
	}

	@Override
	public CommandArgument getDestinationPath() {
		return destPath;
	}

	public CompiledArgument sourceContextAsArgument() {
		return new CompiledArgument(CopyCommand.contextToString(sourceContext));
	}

	public CompiledArgument destContextAsArgument() {
		return new CompiledArgument(CopyCommand.contextToString(destContext));
	}

	@Override
	public List<CompiledArgument> normalise() {
		return List.of(
				this.sourceContextAsArgument(),
				this.sourcePath,
				this.destContextAsArgument(),
				this.destPath
		);
	}

	public static CompiledCopyCommand resolveArguments(List<CompiledArgument> args) throws IllegalArgumentException {
		if(args.size() != 4) {
			throw new IllegalArgumentException();
		}

		return new CompiledCopyCommand(
				CopyCommand.stringToContext(args.get(0).getText()),
				args.get(1),
				CopyCommand.stringToContext(args.get(2).getText()),
				args.get(3)
		);
	}
}
