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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class AddJobs extends NimrodCLICommand {

	@Override
	public String getCommand() {
		return "addjobs";
	}

	private static JsonArray sadfasdf(InputStream is) {
		try(JsonReader r = Json.createReader(is)) {
			return r.readArray();
		}
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		String expName = args.getString("exp_name");
		String jobsFile = args.getString("jobsfile");

		/* Load the jobsfile */
		JsonArray ja;
		if(jobsFile.equals("-")) {
			ja = sadfasdf(System.in);
		} else {
			try(InputStream is = Files.newInputStream(Paths.get(jobsFile))) {
				ja = sadfasdf(is);
			}
		}

		List<Map<String, String>> jobs = JsonUtils.jobsFileFromJson(ja);

		/* Do some quick validation before hitting the db. */
		Set<Set<String>> sss = jobs.stream()
				.map(j -> j.keySet())
				.collect(Collectors.toSet());
		if(sss.size() != 1) {
			err.printf("Mismatched variable names.\n");
			return 1;
		}

		/* Now add it */
		Experiment exp = nimrod.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'.\n", expName);
			return 1;
		}

		if(!sss.stream().findFirst().get().equals(exp.getVariables())) {
			err.printf("Job variables don't match run variables.\n");
			return 1;
		}

		nimrod.addJobs(exp, jobs);
		return 0;

	}

	public static final CommandEntry DEFINITION = new CommandEntry(new AddJobs(), "Add a list of jobs to the given run.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);
			addExpNameArg(parser);

			parser.addArgument("jobsfile")
					.type(String.class)
					.help("The jobsfile path. Omit or use '-' to read from stdin.")
					.setDefault("-")
					.nargs("?");
		}

	};
}
