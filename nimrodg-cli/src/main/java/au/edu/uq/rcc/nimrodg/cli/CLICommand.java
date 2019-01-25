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
package au.edu.uq.rcc.nimrodg.cli;

import java.io.PrintStream;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Anything that implements this can be used by the CLI frontend.
 *
 */
public interface CLICommand {

	/**
	 * Get the unique name of the command.
	 *
	 * @return The unique name of the command.
	 */
	String getCommand();

	/**
	 * Execute the command.
	 *
	 * @param args The parsed arguments.
	 * @param out The {@link PrintStream} where all "standard output" should go.
	 * @param err The {@link PrintStream} where all "standard error" should go.
	 * @return The execution status.
	 * @throws Exception
	 */
	int execute(Namespace args, PrintStream out, PrintStream err) throws Exception;

}
