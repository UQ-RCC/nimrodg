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
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand.Stream;
import java.util.ArrayList;
import java.util.List;

public class CompiledRedirectCommand extends CompiledCommand implements RedirectCommand {

	public final Stream stream;

	public final boolean append;
	public final CompiledArgument file;

	CompiledRedirectCommand(Stream stream, boolean append, CompiledArgument file) {
		super(Command.Type.Redirect);
		this.stream = stream;
		this.append = append;
		this.file = file;
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
	public CommandArgument getFile() {
		return file;
	}

	public CompiledArgument streamAsArgument() {
		return new CompiledArgument(RedirectCommand.streamToString(stream));
	}

	public CompiledArgument appendAsArgument() {
		return new CompiledArgument(Boolean.toString(append));
	}

	@Override
	public List<CompiledArgument> normalise() {
		List<CompiledArgument> args = new ArrayList<>();
		args.add(this.streamAsArgument());
		args.add(this.appendAsArgument());
		args.add(this.file);
		return args;
	}

	public static CompiledRedirectCommand resolveArguments(List<CompiledArgument> args) throws IllegalArgumentException {
		if(args.size() != 3) {
			throw new IllegalArgumentException();
		}

		return new CompiledRedirectCommand(
				RedirectCommand.stringToStream(args.get(0).getText()),
				Boolean.parseBoolean(args.get(1).getText()),
				args.get(2)
		);
	}
}
