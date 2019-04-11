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

import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.cli.CLICommand;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class GenConfig implements CLICommand {

	@Override
	public String getCommand() {
		return "genconfig";
	}

	@Override
	public int execute(Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {
		Path config = Paths.get(args.getString("config"));

		byte[] rawCfg;
		try(InputStream is = NimrodCLI.class.getResourceAsStream("nimrod-ini-sample.ini")) {
			rawCfg = is.readAllBytes();
		}

		if(args.getBoolean("force")) {
			Files.write(config, rawCfg, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} else {
			Files.write(config, rawCfg, StandardOpenOption.CREATE_NEW);
		}

		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new GenConfig(), "Generate a default configuration file.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			parser.addArgument("--force", "-f")
					.dest("force")
					.type(Boolean.class)
					.action(Arguments.storeTrue())
					.help("Force creation if already exists.");
		}
	};
}
