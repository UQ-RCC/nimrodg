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
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.utils.XDGDirs;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public final class Staging extends DefaultCLICommand {

	public static final NimrodParseAPI PARSE_API = ANTLR4ParseAPIImpl.INSTANCE;

	@Override
	public String getCommand() {
		return "staging";
	}

	private Method lookupNimrodMethod(String name) {
		try {
			return this.getClass().getDeclaredMethod(name, UserConfig.class, NimrodAPI.class, PrintStream.class, PrintStream.class, Path[].class, String[].class);
		} catch(NoSuchMethodException | SecurityException e) {
			/* nop */
		}
		return null;
	}

	private Method lookupNonNimrodMethod(String name) {
		try {
			return this.getClass().getDeclaredMethod(name, UserConfig.class, PrintStream.class, PrintStream.class, Path[].class, String[].class);
		} catch(NoSuchMethodException | SecurityException e) {
			/* nop */
		}
		return null;
	}

	@Override
	public int execute(Namespace args, UserConfig config, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		String scommand = args.getString("scommand");

		boolean hasNimrodParameter = true;
		Method m = lookupNimrodMethod(scommand);
		if(m == null) {
			m = lookupNonNimrodMethod(scommand);
			hasNimrodParameter = false;
		}

		if(m == null) {
			err.printf("No such staging method '%s'.\n", scommand);
			return 1;
		}

		try {
			if(hasNimrodParameter) {
				try(NimrodAPI nimrod = NimrodCLICommand.createFactory(config).createNimrod(config)) {
					m.invoke(this, config, nimrod, out, err, configDirs, args.getList("args").toArray(new String[args.getList("args").size()]));
				}
			} else {
				m.invoke(this, config, out, err, configDirs, args.getList("args").toArray(new String[args.getList("args").size()]));
			}

		} catch(IllegalAccessException | IllegalArgumentException e) {
			e.printStackTrace(err);
			return 1;
		} catch(InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if(t instanceof NimrodException) {
				throw (NimrodException)t;
			} else if(t instanceof IOException) {
				throw (IOException)t;
			} else if(t instanceof RuntimeException) {
				throw (RuntimeException)t;
			}
			e.printStackTrace(err);
			return 1;
		}

		return 0;
	}

	public void goodTest(UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs, String[] args) throws Exception {
		CompiledRun cr = PARSE_API.parseRunToBuilder(
				"parameter x integer range from 1 to 1000 step 1\n"
				+ "\n"
				+ "task main\n"
				+ "	onerror ignore\n"
				+ "	redirect stdout to output.txt\n"
				+ "	redirect stderr append to output.txt\n"
				+ "	exec echo $x\n"
				+ "	copy node:output.txt root:output-$x.txt\n"
				+ "endtask").build();

		Experiment exp1 = nimrod.getExperiment("exp1");
		if(exp1 != null) {
			nimrod.deleteExperiment(exp1);
		}
		exp1 = nimrod.addExperiment("exp1", cr);

		Resource local = createLocal(nimrod, "local", "x86_64-pc-linux-musl", 10);
		nimrod.assignResource(local, exp1);
	}

	public void singleAgentSleep(UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs, String[] args) throws Exception {
		nAgentSleep(config, nimrod, out, err, configDirs, new String[]{"1", "100"});
	}

	public void nAgentSleep(UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs, String[] args) throws Exception {

		if(args.length > 2) {
			err.print("Invalid arguments\n");
			return;
		}

		long length = -1;
		if(args.length == 2) {
			length = Long.parseUnsignedLong(args[1]);
		}

		if(length <= 0) {
			length = 100;
		}

		int nAgents = -1;
		if(args.length >= 1) {
			nAgents = Integer.parseUnsignedInt(args[0]);
		}

		if(nAgents <= 0) {
			nAgents = 1;
		}

		Experiment exp1 = nimrod.getExperiment("exp1");
		if(exp1 != null) {
			nimrod.deleteExperiment(exp1);
		}

		CompiledRun cr = PARSE_API.parseRunToBuilder(
				String.format("parameter x integer range from 0 to 10000 step 1\n"
						+ "task main\n"
						+ "    onerror fail\n"
						+ "    shexec \"sleep %d\"\n"
						+ "endtask", length)).build();

		exp1 = nimrod.addExperiment("exp1", cr);

		Resource local = createLocal(nimrod, "local", "x86_64-pc-linux-musl", nAgents);
		nimrod.assignResource(local, exp1);
	}

	public void bigGet(UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs, String[] args) throws Exception {
		Experiment exp1 = nimrod.getExperiment("exp1");
		if(exp1 != null) {
			nimrod.deleteExperiment(exp1);
		}

		CompiledRun cr = PARSE_API.parseRunToBuilder(
			"parameter x integer range from 0 to 10000 step 1\n"
					+ "task main\n"
					+ "    onerror fail\n"
					+ "endtask").build();

		exp1 = nimrod.addExperiment("exp1", cr);

		//benchFiltering(nimrod, exp1, 10000, 0, 10000);

		// Pre-optimisation, ~111 seconds on an NVME SSD, ~948 seconds on HDD
		//benchAttemptCreation((NimrodMasterAPI)nimrod, exp1, 0, 10000);

		// 0.73 seconds (⌐■_■)
		benchAttemptCreationBatch((NimrodMasterAPI)nimrod, exp1, 0, 10000);
	}

	private void benchAttemptCreationBatch(NimrodMasterAPI nimrod, Experiment exp1, int start, int limit) {
		Collection<Job> jobs = nimrod.filterJobs(exp1, EnumSet.allOf(JobAttempt.Status.class), start, limit);

		long startTime = System.currentTimeMillis();
		Collection<JobAttempt> atts = nimrod.createJobAttempts(jobs);
		long endTime = System.currentTimeMillis();
		System.err.printf("Took %f seconds\n", (endTime - startTime) / 1000.0);

		int x = 0;
	}

	private void benchAttemptCreation(NimrodMasterAPI nimrod, Experiment exp1, int start, int limit) {
		Collection<Job> jobs = nimrod.filterJobs(exp1, EnumSet.allOf(JobAttempt.Status.class), start, limit);

		long totalTime = 0;
		int i = 0;
		for(Job j : jobs) {
			long startTime = System.currentTimeMillis();
			nimrod.createJobAttempts(List.of(j));
			long endTime = System.currentTimeMillis();

			totalTime += endTime - startTime;
			++i;
		}

		System.err.printf("Took %f seconds\n", totalTime / 1000.0);
	}

	private void benchFiltering(NimrodAPI nimrod, Experiment exp1, int numTimes, int start, int limit) {
		double secs = 0.0;
		for(int i = 0; i < numTimes; ++i) {
			long startTime = System.currentTimeMillis();

			Collection<Job> jobs = nimrod.filterJobs(exp1, EnumSet.allOf(JobAttempt.Status.class), start, limit);
			long endTime = System.currentTimeMillis();

			double time = (endTime - startTime) / 1000.0;
			secs += time;
			//System.err.printf("Took %f seconds\n", (endTime - startTime) / 1000.0f);
		}

		System.err.printf("Took %f seconds\n", secs / numTimes);
	}

	public int xdgDump(UserConfig config, PrintStream out, PrintStream err, Path[] configDirs, String[] args) {
		XDGDirs xdg = XDGDirs.resolve();

		JsonArrayBuilder jcfg = Json.createArrayBuilder();
		for(Path p : xdg.configDirs) {
			jcfg.add(p.toString());
		}

		JsonArrayBuilder jdata = Json.createArrayBuilder();
		for(Path p : xdg.dataDirs) {
			jdata.add(p.toString());
		}

		prettyPrint(Json.createObjectBuilder()
				.add("XDG_CONFIG_HOME", xdg.configHome.toString())
				.add("XDG_CONFIG_DIRS", jcfg)
				.add("XDG_CACHE_HOME", xdg.cacheHome.toString())
				.add("XDG_DATA_HOME", xdg.dataHome.toString())
				.add("XDG_DATA_DIRS", jdata)
				.build(), out);
		return 0;
	}

	public static void main(String[] args) throws Exception {
		//System.exit(NimrodCLI.cliMain(new String[]{"-d", "staging", "nAgentSleep", "6", "60"}));
		System.exit(NimrodCLI.cliMain(new String[]{"-d", "staging", "bigGet"}));
	}

	private static Resource createLocal(NimrodAPI api, String name, String plat, int count) {
		Resource res = api.getResource(name);
		if(res != null) {
			api.deleteResource(res);
		}
		return api.addResource(
				name,
				"local",
				Json.createObjectBuilder()
						.add("parallelism", count <= 0 ? Runtime.getRuntime().availableProcessors() : count)
						.add("platform", plat)
						.add("capture_mode", "stream")
						.build(),
				null,
				NimrodURI.create(Paths.get(api.getConfig().getRootStore()).toUri(), null, null, null)
		);
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new Staging(), "Execute staging commands.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);
			parser.addArgument("scommand")
					.type(String.class);
			parser.addArgument("args")
					.nargs("*");
		}

	};
}
