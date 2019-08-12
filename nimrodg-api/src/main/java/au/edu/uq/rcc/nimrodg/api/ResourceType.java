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
package au.edu.uq.rcc.nimrodg.api;

import java.io.PrintStream;
import java.nio.file.Path;
import javax.json.JsonStructure;

/**
 * Represents a "type" of resource. This contains the type name and factory class used to manage it.
 */
public interface ResourceType {

	String getName();

	/**
	 * Parse a sequence of command-line arguments into a resource-specific configuration valid for this resource type.
	 *
	 * @param ap
	 * @param args A list of command-line arguments.
	 * @param out stdout
	 * @param err stderr
	 * @param configDirs Directories to search for configuration files
	 * @return A resource-specific configuration structure, or null if parsing fails.
	 */
	JsonStructure parseCommandArguments(AgentProvider ap, String[] args, PrintStream out, PrintStream err, Path[] configDirs);
}
