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
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import java.util.ArrayList;
import java.util.List;

public final class CompiledOnErrorCommand extends CompiledCommand {

	public final OnErrorCommand.Action action;

	CompiledOnErrorCommand(OnErrorCommand.Action act) {
		super(Command.Type.OnError);
		action = act;
	}

	public CompiledArgument actionAsArgument() {
		return new CompiledArgument(OnErrorCommand.actionToString(action));
	}

	@Override
	public List<CompiledArgument> normalise() {
		List<CompiledArgument> args = new ArrayList<>();
		args.add(this.actionAsArgument());
		return args;
	}

	public static CompiledOnErrorCommand resolveArguments(List<CompiledArgument> args) throws IllegalArgumentException {
		if(args.size() != 1) {
			throw new IllegalArgumentException();
		}

		return new CompiledOnErrorCommand(OnErrorCommand.stringToAction(args.get(0).text));
	}
}
