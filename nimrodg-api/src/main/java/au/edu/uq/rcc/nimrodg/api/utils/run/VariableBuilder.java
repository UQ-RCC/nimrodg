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

import java.util.ArrayList;
import java.util.List;

public class VariableBuilder {

	private String name;
	private String label;
	private int index;
	private final List<String> values;

	public VariableBuilder() {
		this.values = new ArrayList<>();
	}

	public VariableBuilder(VariableBuilder vb) {
		this.name = vb.name;
		this.index = vb.index;
		this.label = vb.label;
		this.values = new ArrayList<>(vb.values);
	}

	public VariableBuilder name(String name) {
		this.name = name;
		return this;
	}

	public VariableBuilder label(String label) {
		this.label = label;
		return this;
	}

	public VariableBuilder index(int index) {
		this.index = index;
		return this;
	}

	public VariableBuilder addValue(String value) {
		this.values.add(value);
		return this;
	}

	public VariableBuilder addValues(List<String> values) {
		this.values.addAll(values);
		return this;
	}

	public CompiledVariable build() {
		return new CompiledVariable(name, index, values);
	}
}
