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

import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;

public class CopyCommandBuilder {

	private CopyCommand.Context sourceContext;
	private String sourcePath;

	private CopyCommand.Context destContext;
	private String destPath;

	public CopyCommandBuilder() {

	}

	public CopyCommandBuilder sourceContext(CopyCommand.Context context) {
		sourceContext = context;
		return this;
	}

	public CopyCommandBuilder sourcePath(String path) {
		sourcePath = path;
		return this;
	}

	public CopyCommandBuilder destContext(CopyCommand.Context context) {
		destContext = context;
		return this;
	}

	public CopyCommandBuilder destPath(String path) {
		destPath = path;
		return this;
	}

	public CompiledCopyCommand build() throws SubstitutionException {
		return new CompiledCopyCommand(
				sourceContext,
				new CompiledArgument(sourcePath, StringUtils.findSubstitutions(sourcePath)),
				destContext,
				new CompiledArgument(destPath, StringUtils.findSubstitutions(destPath)));
	}

}
