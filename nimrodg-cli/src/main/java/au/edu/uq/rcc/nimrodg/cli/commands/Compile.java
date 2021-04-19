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
package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.cli.CLICommand;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public final class Compile implements CLICommand {

	private Compile() {

	}

	@Override
	public String getCommand() {
		return "compile";
	}

	@Override
	public int execute(Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		String runFile = args.getString("planfile");

		NimrodParseAPI parseApi = ANTLR4ParseAPIImpl.INSTANCE;
		/* Load the runfile */
		RunBuilder b;
		try {
			if(runFile.equals("-")) {
				b = parseApi.parseRunToBuilder(System.in);
			} else {
				b = parseApi.parseRunToBuilder(Paths.get(runFile));
			}
		} catch(PlanfileParseException ex) {
			ex.getErrors().forEach(e -> System.err.println(e.toString(runFile)));
			return 1;
		}

		CompiledRun rf;
		try {
			rf = b.build();
		} catch(RunfileBuildException e) {
			throw new NimrodException(e);
		}

		err.println("Successfully compiled planfile:");
		err.printf("  %d variables, %d jobs, %d tasks\n", rf.numVariables, rf.numJobs, rf.numTasks);

		if(!args.getBoolean("noout")) {
			out.print(JsonUtils.toJson(rf).toString());
		}
		out.println();
		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new Compile(), "Compile a planfile.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			parser.addArgument("--no-out")
					.dest("noout")
					.type(Boolean.class)
					.action(Arguments.storeTrue())
					.help("Disable JSON output.");

			parser.addArgument("planfile")
					.metavar("planfile.pln")
					.type(String.class)
					.help("The planfile path. Omit or use '-' to read from stdin.")
					.setDefault("-")
					.nargs("?");
		}

	};
}
