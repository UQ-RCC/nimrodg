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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class PortalAPI extends NimrodCLICommand {

	@Override
	public String getCommand() {
		return "portalapi";
	}

	private Method lookupMethod(String name) {
		try {
			return this.getClass().getDeclaredMethod(name, String[].class, NimrodAPI.class, PrintStream.class, PrintStream.class);
		} catch(NoSuchMethodException | SecurityException e) {

		}
		return null;
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodAPIException {
		List<String> aargs = args.getList("args");
		if(aargs.isEmpty()) {
			return 2;
		}
		String cmd = aargs.get(0);
		aargs.remove(0);
		String[] cmdArgs = aargs.toArray(new String[aargs.size()]);

		Method m = lookupMethod(cmd);
		if(m == null) {
			err.printf("No such portalapi command '%s'.\n", cmd);
			return 2;
		}

		try {
			return (Integer)m.invoke(this, cmdArgs, nimrod, out, err);
		} catch(IllegalAccessException | IllegalArgumentException e) {
			e.printStackTrace(err);
			return 1;
		} catch(InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if(t instanceof NimrodAPIException) {
				throw (NimrodAPIException)t;
			} else if(t instanceof IOException) {
				throw (IOException)t;
			} else if(t instanceof RuntimeException) {
				throw (RuntimeException)t;
			}
			e.printStackTrace(err);
			return 1;
		}
	}

	public int getexperiments(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		Collection<Experiment> exps = nimrod.getExperiments();
		String rootDir = nimrod.getConfig().getRootStore();
		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			writeExperimentHeader(csv);
			for(Experiment exp : exps) {
				writeExperiment(rootDir, exp, csv);
			}
		}

		return 0;
	}

	public int getresources(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		Collection<Resource> roots = nimrod.getResources();

		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			writeResourceHeader(csv);

			for(Resource root : roots) {
				writeResource(root, csv);
			}
		}
		return 0;
	}

	public int addexperiment(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		NimrodParseAPI parseApi = ANTLR4ParseAPIImpl.INSTANCE;
		RunBuilder b;
		try {
			b = parseApi.parseRunToBuilder(System.in);
		} catch(PlanfileParseException e) {
			e.printStackTrace(System.err);
			return 1;
		}

		CompiledRun cr;
		try {
			cr = b.build();
		} catch(RunBuilder.RunfileBuildException e) {
			e.printStackTrace(err);
			return 1;
		}

		String rootDir = nimrod.getConfig().getRootStore();
		Experiment exp = nimrod.addExperiment(args[0], cr);
		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			writeExperimentHeader(csv);
			writeExperiment(rootDir, exp, csv);
		}
		return 0;
	}

	public int addjobs(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		Experiment exp = nimrod.getExperiment(args[0]);
		if(exp == null) {
			err.printf("No such experiment '%s'\n", args[0]);
			return 1;
		}

		JsonArray ja;
		try(JsonReader r = Json.createReader(System.in)) {
			ja = r.readArray();
		}

		List<Map<String, String>> jjobs = JsonUtils.jobsFileFromJson(ja);

		/* Do some quick validation before hitting the db. */
		Set<Set<String>> sss = jjobs.stream()
				.map(j -> j.keySet())
				.collect(Collectors.toSet());
		if(sss.size() != 1) {
			err.printf("Mismatched variable names.\n");
			return 1;
		}

		if(!sss.stream().findFirst().get().equals(exp.getVariables())) {
			err.printf("Job variables don't match run variables.\n");
			return 1;
		}

		Collection<Job> jobs = nimrod.addJobs(exp, jjobs);

		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			csvWriteHeader(csv, "index", "creation_time", "status", "variables");
			for(Job j : jobs) {
				writeJob(j, csv);
			}
		}
		return 0;
	}

	public int deleteexperiment(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		Experiment exp = nimrod.getExperiment(args[0]);
		if(exp == null) {
			return 0;
		}

		nimrod.deleteExperiment(exp);
		return 0;
	}

	public int addresource(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		// path type [amqpUri [amqpCert [amqpNoVerifyPeer [amqpNoVerifyHost [txUri [txCert [txNoVerifyPeer [txNoVerifyHost]]]]]]]]
		if(args.length < 2) {
			return 2;
		}

		String path = args[0];
		if(path.contains("/")) {
			err.printf("Only root resources are supported.\n");
			return 1;
		}

		Resource node = nimrod.getResource(path);
		if(node != null) {
			err.printf("Resource already exists.\n");
			return 1;
		}

		URI _amqpUri = null;
		String _amqpCert = null;
		Boolean _amqpNoVerifyPeer = null, _amqpNoVerifyHost = null;

		{
			if(args.length >= 3) {
				try {
					_amqpUri = new URI(args[2]);
				} catch(URISyntaxException e) {
					e.printStackTrace(err);
					return 2;
				}
			}

			if(args.length >= 4) {
				_amqpCert = args[3];
			}

			if(args.length >= 5) {
				_amqpNoVerifyPeer = Boolean.parseBoolean(args[4]);
			}

			if(args.length >= 6) {
				_amqpNoVerifyHost = Boolean.parseBoolean(args[5]);
			}
		}

		URI _txUri = null;
		String _txCert = null;
		Boolean _txNoVerifyPeer = null, _txNoVerifyHost = null;
		{
			if(args.length >= 7) {
				try {
					_txUri = new URI(args[6]);
				} catch(URISyntaxException e) {
					e.printStackTrace(err);
					return 2;
				}
			}

			if(args.length >= 8) {
				_txCert = args[7];
			}

			if(args.length >= 9) {
				_txNoVerifyPeer = Boolean.parseBoolean(args[8]);
			}

			if(args.length >= 10) {
				_txNoVerifyHost = Boolean.parseBoolean(args[9]);
			}
		}

		JsonStructure config;
		try(JsonReader r = Json.createReader(System.in)) {
			config = r.read();
		}

		node = nimrod.addResource(
				path,
				args[1],
				config,
				NimrodURI.create(_amqpUri, _amqpCert, _amqpNoVerifyPeer, _amqpNoVerifyHost),
				NimrodURI.create(_txUri, _txCert, _txNoVerifyPeer, _txNoVerifyHost)
		);

		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			writeResourceHeader(csv);
			writeResource(node, csv);
		}

		return 0;
	}

	public int getresourceagents(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		if(!nimrod.getAPICaps().master) {
			err.printf("Implementation doesn't have master capabilities.\n");
			return 1;
		}

		Resource node = nimrod.getResource(args[0]);
		if(node == null) {
			err.printf("No such resource '%s'\n", args[0]);
			return 1;
		}

		/* FIXME: Agent querying facilities should be in the client-side api. */
		NimrodMasterAPI mapi = (NimrodMasterAPI)nimrod;

		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			csvWriteHeader(csv, "state", "queue", "uuid", "shutdown_signal", "shutdown_reason",
					"created", "connected_at", "last_heard_from", "expiry_time", "expired",
					"actuator_data"
			);

			for(AgentState as : mapi.getResourceAgents(node)) {
				csv.print(Agent.stateToString(as.getState()));
				csv.print(as.getQueue());
				csv.print(as.getUUID());
				csv.print(as.getShutdownSignal());
				csv.print(AgentShutdown.reasonToString(as.getShutdownReason()));
				csv.print(as.getCreationTime().getEpochSecond());
				if(as.getConnectionTime() != null) {
					csv.print(as.getCreationTime().toEpochMilli() / 1000);
				} else {
					csv.print("");
				}

				if(as.getLastHeardFrom() != null) {
					csv.print(as.getLastHeardFrom().getEpochSecond());
				} else {
					csv.print("");
				}

				csv.print(as.getExpiryTime().getEpochSecond());
				csv.print(as.getExpired());

				if(as.getActuatorData() != null) {
					csv.print(as.getActuatorData().toString());
				} else {
					csv.print("");
				}

				csv.println();

			}
		}
		return 0;
	}

	public int deleteresource(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		String path = args[0];
		if(path.contains("/")) {
			err.printf("Only root resources are supported.\n");
			return 1;
		}

		Resource node = nimrod.getResource(path);
		if(node == null) {
			return 0;
		}

		nimrod.deleteResource(node);

		return 0;
	}

	public int assign(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 2) {
			return 2;
		}

		Resource node = nimrod.getResource(args[0]);
		if(node == null) {
			err.printf("No such resource '%s'\n", args[0]);
			return 1;
		}

		Experiment exp = nimrod.getExperiment(args[1]);
		if(exp == null) {
			err.printf("No such experiment '%s'\n", args[1]);
			return 1;
		}

		nimrod.assignResource(node, exp);
		return 0;
	}

	public int unassign(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 2) {
			return 2;
		}

		Resource node = nimrod.getResource(args[0]);
		if(node == null) {
			err.printf("No such resource '%s'\n", args[0]);
			return 1;
		}

		Experiment exp = nimrod.getExperiment(args[1]);
		if(exp == null) {
			err.printf("No such experiment '%s'\n", args[1]);
			return 1;
		}

		nimrod.unassignResource(node, exp);
		return 0;
	}

	public int getassignments(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length < 1) {
			return 2;
		}

		Experiment exp = nimrod.getExperiment(args[0]);
		if(exp == null) {
			err.printf("No such experiment '%s'\n", args[0]);
			return 1;
		}

		Collection<Resource> ress = nimrod.getAssignedResources(exp);

		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			writeResourceHeader(csv);
			for(Resource n : ress) {
				writeResource(n, csv);
			}
		}
		return 0;
	}

	public int compile(String[] args, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException {
		if(args.length != 0) {
			return 2;
		}

		NimrodParseAPI parseApi = ANTLR4ParseAPIImpl.INSTANCE;
		try(CSVPrinter csv = new CSVPrinter(out, CSVFormat.RFC4180)) {
			csvWriteHeader(csv, "line", "position", "message");

			RunBuilder b;
			try {
				b = parseApi.parseRunToBuilder(System.in);
			} catch(PlanfileParseException ex) {
				for(PlanfileParseException.ParseError e : ex.getErrors()) {
					csv.print(e.line);
					csv.print(e.position);
					csv.print(e.message);
					csv.println();
				}
				return 1;
			}

			try {
				b.build();
			} catch(RunBuilder.RunfileBuildException e) {
				csv.print(-1);
				csv.print(-1);
				csv.print(e.getMessage());
				csv.println();
				return 1;
			}
		}
		return 0;
	}

	private static void csvWriteHeader(CSVPrinter csv, String... fields) throws IOException {
		for(String f : fields) {
			csv.print(f);
		}

		csv.println();
	}

	private static void writeExperimentHeader(CSVPrinter csv) throws IOException {
		csvWriteHeader(csv, "name", "state", "work_dir", "creation_time", "token", "path");
	}

	private static void writeExperiment(String rootDir, Experiment exp, CSVPrinter csv) throws IOException {
		csv.print(exp.getName());
		csv.print(Experiment.stateToString(exp.getState()));
		csv.print(Paths.get(rootDir).resolve(exp.getWorkingDirectory()).toString());
		csv.print(exp.getCreationTime().getEpochSecond());
		csv.print(exp.getToken());
		csv.print(exp.getPath());
		csv.println();
	}

	private static void writeJob(Job j, CSVPrinter csv) throws IOException {
		csv.print(j.getIndex());
		csv.print(j.getCreationTime().getEpochSecond());
		csv.print(j.getStatus());

		JsonObjectBuilder vars = Json.createObjectBuilder();
		j.getVariables().entrySet().forEach(e -> vars.add(e.getKey(), e.getValue()));

		csv.print(vars.build().toString());
		csv.println();
	}

	private static void writeResourceHeader(CSVPrinter csv) throws IOException {
		csvWriteHeader(csv, "name", "type", "config",
				"amqp_uri", "amqp_cert", "amqp_no_verify_peer", "amqp_no_verify_host",
				"tx_uri", "tx_cert", "tx_no_verify_peer", "tx_no_verify_host"
		);
	}

	private static void writeResource(Resource n, CSVPrinter csv) throws IOException {
		csv.print(n.getName());
		csv.print(n.getTypeName());
		csv.print(n.getConfig());
		writeNimrodUri(n.getAMQPUri(), csv);
		writeNimrodUri(n.getTransferUri(), csv);
		csv.println();
	}

	private static void writeNimrodUri(NimrodURI uri, CSVPrinter csv) throws IOException {
		if(uri == null) {
			for(int i = 0; i < 4; ++i) {
				csv.print("");
			}
		} else {
			csv.print(uri.uri);
			csv.print(uri.certPath);
			csv.print(uri.noVerifyPeer);
			csv.print(uri.noVerifyHost);
		}
	}

	public static void main(String[] args) throws Exception {
		System.exit(NimrodCLI.cliMain(new String[]{"portalapi", "getresourceagents", "root"}));
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new PortalAPI(), "Portal API") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			parser.addArgument("args")
					.type(String.class)
					.nargs("*");
		}

	};
}
