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
import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledArgument;
import java.util.ArrayList;
import java.util.List;

public class CommandArgumentImpl implements CommandArgument {

	private CommandArgumentImpl(CommandImpl cmd, int index, String text) {
		this.command = cmd;
		this.index = index;
		this.text = text;
		this.substitutions = new ArrayList<>();
	}

	private final CommandImpl command;
	private final int index;
	private final String text;
	private final List<SubstitutionImpl> substitutions;

	static CommandArgumentImpl create(CommandImpl cmd, int index, String text, List<au.edu.uq.rcc.nimrodg.api.utils.Substitution> subs) {
		CommandArgumentImpl arg = new CommandArgumentImpl(cmd, index, text);
		subs.forEach(sub -> arg.substitutions.add(new SubstitutionImpl(sub.variable(), sub)));
		return arg;
	}

	static CommandArgumentImpl create(CommandImpl cmd, int index, CompiledArgument arg) {
		return create(cmd, index, arg.text, arg.substitutions);
	}

	@Override
	public Command getCommand() {
		return command.getCommand();
	}

	@Override
	public String getText() {
		return text;
	}

	@Override
	public int getIndex() {
		return index;
	}

	@Override
	public List<SubstitutionImpl> getSubstitutions() {
		return substitutions;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 97 * hash + command.hashCode();
		hash = 97 * hash + index;
		hash = 97 * hash + text.hashCode();
		hash = 97 * hash + substitutions.hashCode();
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof CommandArgumentImpl)) {
			return false;
		}

		CommandArgumentImpl o = ((CommandArgumentImpl)obj);

		return command.equals(command)
				&& index == o.index
				&& text.equals(o.text)
				&& substitutions.equals(o.substitutions);
	}

}
