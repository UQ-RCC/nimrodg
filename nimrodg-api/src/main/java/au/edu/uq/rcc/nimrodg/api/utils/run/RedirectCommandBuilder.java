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

import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;

public class RedirectCommandBuilder {

	private RedirectCommand.Stream stream;
	private boolean append;
	private String file;

	public RedirectCommandBuilder() {

	}

	public RedirectCommandBuilder stream(RedirectCommand.Stream stream) {
		this.stream = stream;
		return this;
	}

	public RedirectCommandBuilder file(String file) {
		this.file = file;
		return this;
	}

	public RedirectCommandBuilder append(boolean append) {
		this.append = append;
		return this;
	}

	public CompiledRedirectCommand build() throws SubstitutionException {
		return new CompiledRedirectCommand(stream, append, new CompiledArgument(file, StringUtils.findSubstitutions(file)));
	}
}
