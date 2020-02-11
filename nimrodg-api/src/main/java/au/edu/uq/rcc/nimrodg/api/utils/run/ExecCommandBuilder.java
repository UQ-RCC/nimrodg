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

import au.edu.uq.rcc.nimrodg.api.Substitution;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;
import java.util.ArrayList;
import java.util.List;

public class ExecCommandBuilder {

	private String program;
	private boolean searchPath;
	private final List<String> arguments;
	
	public ExecCommandBuilder() {
		this.arguments = new ArrayList<>();
	}

	public ExecCommandBuilder program(String program) {
		this.program = program;
		return this;
	}

	public ExecCommandBuilder searchPath(boolean searchPath) {
		this.searchPath = searchPath;
		return this;
	}
	
	public ExecCommandBuilder addArgument(String argument) {
		this.arguments.add(argument);
		return this;
	}
	
	public ExecCommandBuilder addArguments(List<String> args) {
		this.arguments.addAll(args);
		return this;
	}
	
	public CompiledExecCommand build() throws SubstitutionException {
		List<CompiledArgument> args = new ArrayList<>(arguments.size());
		
		for(String arg : arguments) {
			args.add(new CompiledArgument(arg, StringUtils.findSubstitutions(arg)));
		}
		
		return new CompiledExecCommand(program, args, searchPath);
	}
}
