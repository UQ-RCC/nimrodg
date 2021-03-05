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
package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;

import java.io.PrintStream;
import java.nio.file.Path;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class SetProp extends NimrodCLICommand {

	@Override
	public String getCommand() {
		return "setprop";
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		nimrod.setProperty(args.getString("key"), args.getString("value"));
		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new SetProp(), "Set a configuration property.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			parser.addArgument("key")
					.type(String.class)
					.help("The configuration key.")
					.required(true);

			parser.addArgument("value")
					.type(String.class)
					.help("The configuration value.")
					.required(true);
		}
	};
}
