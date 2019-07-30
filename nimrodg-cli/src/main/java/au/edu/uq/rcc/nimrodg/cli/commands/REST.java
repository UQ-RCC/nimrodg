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

import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.rest.RESTService;
import java.io.PrintStream;
import java.nio.file.Path;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class REST extends DefaultCLICommand {

	public static final String DEFAULT_HOSTNAME = "localhost";
	public static final int DEFAULT_LISTEN_PORT = 8080;
	public static final String DEFAULT_CONTEXT_PATH = "/";

	@Override
	public String getCommand() {
		return "rest";
	}

	@Override
	public int execute(Namespace args, UserConfig config, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		NimrodAPIFactory fact = NimrodCLICommand.createFactory(config);

		try(RESTService rs = new RESTService(args.getString("context_path"), args.getString("hostname"), args.getInt("port"), fact, config)) {
			rs.await();
		}

		return 0;
	}

	public static void main(String[] args) throws Exception {
		System.exit(NimrodCLI.cliMain(new String[]{"-d", "rest"}));
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new REST(), "Start the REST service and DAV server.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			parser.addArgument("--hostname")
					.setDefault(DEFAULT_HOSTNAME)
					.type(String.class)
					.help(String.format("Hostname (default: %s)", DEFAULT_HOSTNAME));

			parser.addArgument("--port")
					.setDefault(DEFAULT_LISTEN_PORT)
					.type(Integer.class)
					.help(String.format("Listen port (default: %d)", DEFAULT_LISTEN_PORT));

			parser.addArgument("--context-path")
					.setDefault(DEFAULT_CONTEXT_PATH)
					.type(String.class)
					.help(String.format("Context Path (default: %s)", DEFAULT_CONTEXT_PATH));
		}

	};
}
