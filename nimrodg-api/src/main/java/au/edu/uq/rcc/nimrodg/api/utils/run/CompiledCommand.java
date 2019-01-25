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
import java.util.List;

public abstract class CompiledCommand {

	public final Command.Type type;

	protected CompiledCommand(Command.Type type) {
		this.type = type;
	}

	/**
	 * Convert this command into <em>normalised form</em>, a.k.a. a list of {@link CompiledArgument}
	 *
	 * @return The normalised form of this command.
	 */
	public abstract List<CompiledArgument> normalise();

	/**
	 * Given a command in normalised form, create an actual command instance.
	 *
	 * @param type The intended type of the command.
	 * @param args The list of {@link CompiledArgument}.
	 * @return A command instance that represents the supplied arguments.
	 * @throws IllegalArgumentException if the supplied arguments don't represent a command of the given type.
	 */
	public static final CompiledCommand resolve(Command.Type type, List<CompiledArgument> args) throws IllegalArgumentException {
		switch(type) {
			case OnError:
				return CompiledOnErrorCommand.resolveArguments(args);
			case Redirect:
				return CompiledRedirectCommand.resolveArguments(args);
			case Copy:
				return CompiledCopyCommand.resolveArguments(args);
			case Exec:
				return CompiledExecCommand.resolveArguments(args);
		}

		throw new IllegalArgumentException();
	}
}
