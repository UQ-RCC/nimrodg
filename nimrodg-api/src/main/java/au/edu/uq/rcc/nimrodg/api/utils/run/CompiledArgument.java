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

import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.Substitution;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;

import java.util.Collections;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

public final class CompiledArgument implements CommandArgument {

	private final String text;
	private final List<Substitution> substitutions;

	CompiledArgument(String text) {
		this(text, List.of());
	}

	CompiledArgument(String text, List<Substitution> subs) {
		this.text = text;
		this.substitutions = List.copyOf(subs);
	}

	@Override
	public String getText() {
		return text;
	}

	@Override
	public List<Substitution> getSubstitutions() {
		return substitutions;
	}
}
