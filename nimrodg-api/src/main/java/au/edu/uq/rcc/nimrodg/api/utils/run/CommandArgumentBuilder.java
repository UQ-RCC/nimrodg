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

import au.edu.uq.rcc.nimrodg.api.utils.Substitution;
import java.util.ArrayList;
import java.util.List;

public class CommandArgumentBuilder {

	private String text;
	private final List<Substitution> subs;

	public CommandArgumentBuilder() {
		this("");
	}

	public CommandArgumentBuilder(String text) {
		this.text = text;
		this.subs = new ArrayList<>();
	}

	public CommandArgumentBuilder(String text, List<Substitution> subs) {
		this.text = text;
		this.subs = new ArrayList<>(subs);
	}

	public CommandArgumentBuilder text(String s) {
		this.text = s;
		return this;
	}
	
	public CommandArgumentBuilder addSubstitution(Substitution s) {
		this.subs.add(s);
		return this;
	}

	public CompiledArgument build() {
		return new CompiledArgument(text, subs);
	}

}
