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

import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.ActuatorOpsAdapter;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import au.edu.uq.rcc.nimrodg.resource.HPCResourceType;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import au.edu.uq.rcc.nimrodg.api.Resource;

public class Staging extends DefaultCLICommand {

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
//		Resource awoonga = createAwoonga(nimrod, "awoonga");
//		nimrod.assignResource(awoonga, exp1);
//
//		Resource tinaroo = createTinaroo(nimrod, "tinaroo");
//		nimrod.assignResource(tinaroo, exp1);
//
//		Resource flashlite = createFlashlite(nimrod, "flashlite");
//		nimrod.assignResource(flashlite, exp1);

//		Resource local = createLocal(nimrod, "local", "x86_64-pc-linux-musl", nAgents);
//		nimrod.assignResource(local, exp1);
//
//		Resource[] slaves = createSlaves(nimrod);
//
//		for(int i = 0; i < slaves.length; ++i) {
//			nimrod.assignResource(slaves[i], exp1);
//		}
		HPCResourceType hpcr = new HPCResourceType();
		Resource tinaroo = nimrod.getResource("tinaroo");
		if(tinaroo != null) {
			nimrod.deleteResource(tinaroo);
		}

		JsonObject cfg = hpcr.parseCommandArguments(nimrod, new String[]{
			"--platform", "x86_64-pc-linux-musl",
			"--transport", "openssh",
			"--uri", "ssh://tinaroo1",
			"--limit", "10",
			"--tmpvar", "TMPDIR",
			"--max-batch-size", "10",
			"--type", "pbspro",
			"--ncpus", "1",
			"--mem", "1GiB",
			"--walltime", "24:00:00",
			"--account", "UQ-RCC"
		}, out, err, configDirs).asJsonObject();

		tinaroo = nimrod.addResource("tinaroo", "hpc", cfg, null, null);
		nimrod.assignResource(tinaroo, exp1);

		Resource wiener = nimrod.getResource("wiener");
		if(wiener != null) {
			nimrod.deleteResource(wiener);
		}

		JsonObject wcfg = hpcr.parseCommandArguments(nimrod, new String[]{
			"--platform", "x86_64-pc-linux-musl",
			"--transport", "openssh",
			"--uri", "ssh://wiener",
			"--limit", "10",
			"--tmpvar", "TMPDIR",
			"--max-batch-size", "10",
			"--type", "slurm",
			"--ncpus", "1",
			"--mem", "1GiB",
			"--walltime", "24:00:00"
		}, out, err, configDirs).asJsonObject();

		wiener = nimrod.addResource("wiener", "hpc", wcfg, null, null);
		nimrod.assignResource(wiener, exp1);
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

	private static class NullOps extends ActuatorOpsAdapter {
		public NullOps(NimrodMasterAPI nimrod) {
			super(nimrod);
		}

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {

		}
	}

	public static void main(String[] args) throws Exception {
		//System.exit(NimrodCLI.cliMain(new String[]{"-d", "staging", "nAgentSleep", "6", "60"}));
		System.exit(NimrodCLI.cliMain(new String[]{"-d", "staging", "bigGet"}));
	}

	private static final Path RCC_CLUSTER_KEY_PATH = Paths.get("/home/zane/.ssh/uqzvanim-tinaroo");
	private static final Path WIENER_KEY_PATH = Paths.get("/home/zane/.ssh/uqzvanim_weiner");

	private static Resource createTinaroo(NimrodAPI api, String name) {
		Resource res = api.getResource(name);
		if(res != null) {
			api.deleteResource(res);
		}

		JsonObjectBuilder stor = createBaseRCCConfig("tinaroo.rcc.uq.edu.au", "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAoOXRIriWGPhnMQCH0+Wbx7b2mudzUVL0NwEu8xpr1H8qHdl2bPVa2gIRGoGWRx9eWFBscpdgzBMZQGVdl92oCIXxPtxbQFfMizRTp24eMLqISVN8591QD7gJst+8Ha0lVIZOcIA62fXUIdBfoFG1AflPW4CzgAbUUY3C4Grp3SkbUHGtdW3GzBYwhy2MvcDH2qYCfUoocQaSnW3yMO/LaYyMH1Lr3ayxb5eiUFjl9AO1R9J7S4O3rhh06xt3EJNwkMb8gIpoZYJQDKcgvbUrY9jwLv3eSPd2TSYRAhho18GrCWAQ5NudIVzSP6T8XvRapEgqzNti6g9l4uUG8RXe+w==");
		return api.addResource(name, "pbs", stor.build(), null, null);
	}

	private static Resource createAwoonga(NimrodAPI api, String name) {
		Resource res = api.getResource(name);
		if(res != null) {
			api.deleteResource(res);
		}

		JsonObjectBuilder stor = createBaseRCCConfig("awoonga.rcc.uq.edu.au", "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEArWsPn9ERlHtodr8nLUAWHr9PDYgx2yTuOgk4JO9chjuF8+NnRJojbWzoBC1eU1/h4Ukv5VrUmkn/85RQzBJwCM29H24J/c3D58nVnicwNupN3PslYj8W7XGd1X4uV+aQ+v8kQIr8tC6AXURaXwsAuFBTLQ+ldxAa4hTtkxJvCgZWst0K+v7ibOZe8FOICyc3z1JctvJ49614i1wyioO9yh3pojzoDnySHi/PZq9uMkN9SmqUJWriE9EKibBiWennn/lUHxeIO++fe7B3cuJ686wJtqub2e05PX0zuoxFbVJknyx9qrqkUEXZR9RbNftdKZ8xv0h4O9vv3WYwF3Zqxw==");
		return api.addResource(name, "pbs", stor.build(), null, null);
	}

	private static Resource createFlashlite(NimrodAPI api, String name) {
		Resource res = api.getResource(name);
		if(res != null) {
			api.deleteResource(res);
		}

		JsonObjectBuilder stor = createBaseRCCConfig("flashlite.rcc.uq.edu.au", "ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAplbm/kI54sQLlIdGMH5tgf18Z+d6X3Ik3/y1T4l5ddDN6nPvXkVL4WsKJD2boIWo6L7kBiuhz5KY7AtQrLF+NNDoltP/x2j4jdxGnXTUakt59ARrPCcNPAhINOZMNOHqos2B1T0Ca5ZpYeZDu7yJ25Q1J6OpIayxanPot9MvchXTzJ5/dVvVF092ECuGXA9KfclzV0Al486hcWnEENm7KGxfCYY+46hGGpOCBcc+aHtL5mgNj39tRp7d4tK3cNT39SbAvfmd/V5DnTD8ODaPGS3rISYSWuGw/xQq/vpfGDRGtD4/TmKW1I0O+kn95B56HuZ4jiRQSZli5T6WcMdoWw==");
		return api.addResource(name, "pbs", stor.build(), null, null);
	}

	private static Resource createWiener(NimrodAPI api, String name) {
		Resource res = api.getResource(name);
		if(res != null) {
			api.deleteResource(res);
		}

		JsonObject stor = Json.createObjectBuilder()
				.add("uri", "ssh://uqzvanim@wiener.hpc.net.uq.edu.au")
				.add("keyfile", WIENER_KEY_PATH.toString())
				.add("slurmargs", Json.createArrayBuilder(Arrays.asList(
						"--nodes", "1",
						"--ntasks", "1",
						"--mem-per-cpu", "1g",
						"--cpus-per-task", "1",
						"--ntasks-per-node", "1")).build())
				.add("hostkey", "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBC+yRQJovkwmZNLJ14J2k55VhSWIM8IKlFm6UmSrKD7jfTSp7kuriV+Yi3MC9Bo41+sPaHZRNf+6UWhZ1DNJZOg=")
				.add("limit", 100)
				.add("max_batch_size", 20)
				.build();

		return api.addResource(name, "slurm", stor, null, null);
	}

	private static JsonObjectBuilder createBaseRCCConfig(String hostName, String hostKey) {
		/*
		 * Look in PBSRefGuide14.2.pdf, page 241 for a table of these.
		 * Look in PBSUserGuide14.2.pdf, Section 5.3 for usages.
		 *
		 * NB: Job-wide and chunk resources are mutually exclusive.
		 */
		return Json.createObjectBuilder()
				.add("pbsargs", Json.createArrayBuilder(Arrays.asList("-A", "UQ-RCC")).build())
				.add("batch_dialect", "pbspro")
				.add("batch_config", Json.createArrayBuilder()
						.add(Json.createObjectBuilder()
								.add("name", "walltime")
								.add("value", 3600L)
								.add("scale", false)
						).add(Json.createObjectBuilder()
								.add("name", "ompthreads")
								.add("value", 1)
								.add("scale", false)
						).add(Json.createObjectBuilder()
								.add("name", "mpiprocs")
								.add("value", 1)
								.add("scale", false)
						).add(Json.createObjectBuilder()
								.add("name", "ncpus")
								.add("value", 1)
								.add("scale", true)
						).add(Json.createObjectBuilder()
								.add("name", "mem")
								.add("value", 1073741824L)
								.add("scale", true)
						)
				)
				.add("limit", 100)
				.add("tmpvar", "TMPDIR")
				.add("max_batch_size", 4)
				.add("agent_platform", "x86_64-pc-linux-musl")
				.add("transport", Json.createObjectBuilder()
						.add("uri", String.format("ssh://uqzvanim@%s", hostName))
						.add("keyfile", RCC_CLUSTER_KEY_PATH.toString())
						//.add("name", "sshd")
						//.add("hostkey", hostKey)
						.add("name", "openssh")
						.add("executable", "ssh")
				);
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
