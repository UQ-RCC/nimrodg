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
package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType;
import au.edu.uq.rcc.nimrodg.resource.cluster.HPCActuator;
import com.hubspot.jinjava.Jinjava;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class HPCResourceType extends ClusterResourceType {

	public HPCResourceType() {
		super("hpc", "HPC", "hpcargs");
	}

	@Override
	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {
		super.buildParserBeforeSubmissionArgs(argparser);

		argparser.addArgument("--type")
				.dest("type")
				.type(String.class)
				.required(true)
				.help("The type of the cluster");

		argparser.addArgument("--ncpus")
				.dest("ncpus")
				.type(Long.class)
				.required(true)
				.help("Number of CPUs (cores) used by an individual job");

		argparser.addArgument("--mem")
				.dest("mem")
				.type(String.class)
				.required(true)
				.help("Amount of memory used by an individual job (supports {KMGTPE}[i]{B,b} suffixes)");

		argparser.addArgument("--walltime")
				.dest("walltime")
				.type(String.class)
				.required(true)
				.help("Walltime used by an individual job (supports HH[:MM[:SS]] and [Hh][Mm][Ss])");

		argparser.addArgument("--account")
				.dest("account")
				.type(String.class)
				.required(false)
				.help("Account String");

		argparser.addArgument("--queue")
				.dest("queue")
				.type(String.class)
				.required(false)
				.help("Submission Queue");
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cluster_hpc.json";
	}

	private static boolean validateTemplate(String template, PrintStream out, PrintStream err) {
		/* Do a dummy render to see if the user's messed up. */
		Jinjava jj = HPCActuator.createTemplateEngine();
		Map<String, Object> vars = HPCActuator.createSampleVars();
		try {
			jj.render(template, vars);
		} catch(RuntimeException e) {
			err.printf("Malformed template.\n");
			e.printStackTrace(err);
			return false;
		}

		return true;
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		Map<String, HPCDefinition> hpcDefs;

		try {
			hpcDefs = loadConfig(configDirs, new ArrayList<>());
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		HPCDefinition hpc = hpcDefs.get(ns.getString("type"));
		if(hpc == null) {
			err.printf("Unknown type, valid options are: [%s]", String.join(", ", hpcDefs.keySet()));
			return false;
		}

		valid = validateTemplate(hpc.template, out, err) && valid;

		if(valid) {
			jb.add("definition", hpc.toJson());
		}

		long ncpus = ns.getLong("ncpus");
		long mem = StringUtils.parseMemory(ns.getString("mem"));
		long walltime = StringUtils.parseWalltime(ns.getString("walltime"));

		if(ncpus < 1 || mem < 1 || walltime < 1) {
			err.printf("ncpus, mem, and walltime cannot be < 0.\n");
			valid = false;
		} else {
			jb.add("ncpus", ncpus);
			jb.add("mem", mem);
			jb.add("walltime", walltime);
		}

		String account = ns.getString("account");
		if(account != null) {
			jb.add("account", account);
		}

		Map.Entry<Optional<String>, Optional<String>> queue = StringUtils.parseQueue(ns.getString("queue"));
		queue.getKey().ifPresent(q -> jb.add("queue", q));
		queue.getValue().ifPresent(s -> jb.add("server", s));
		return valid;
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig cfg) throws IOException {
		JsonObject ccfg = node.getConfig().asJsonObject();
		return new HPCActuator(ops, node, amqpUri, certs, new HPCConfig(
				cfg,
				ccfg.getJsonNumber("ncpus").longValue(),
				ccfg.getJsonNumber("mem").longValue(),
				ccfg.getJsonNumber("walltime").longValue(),
				Optional.ofNullable(ccfg.get("account")).map(jv -> ((JsonString)jv).getString()),
				Optional.ofNullable(ccfg.get("queue")).map(jv -> ((JsonString)jv).getString()),
				Optional.ofNullable(ccfg.get("server")).map(jv -> ((JsonString)jv).getString()),
				parseHpcDef("", ccfg.getJsonObject("definition"), true)
		));
	}

	public static class HPCConfig extends ClusterConfig {

		public final long ncpus;
		public final long mem;
		public final long walltime;
		public final Optional<String> account;
		public final Optional<String> queue;
		public final Optional<String> server;
		public final HPCDefinition hpc;

		public HPCConfig(ClusterConfig cfg, long ncpus, long mem, long walltime, Optional<String> account, Optional<String> queue, Optional<String> server, HPCDefinition hpc) {
			super(cfg);
			this.ncpus = ncpus;
			this.mem = mem;
			this.walltime = walltime;
			this.account = account;
			this.queue = queue;
			this.server = server;
			this.hpc = hpc;
		}

		public HPCConfig(HPCConfig cfg) {
			this(cfg, cfg.ncpus, cfg.mem, cfg.walltime, cfg.account, cfg.queue, cfg.server, cfg.hpc);
		}
	}

	public static class HPCDefinition {

		public final String name;
		public final String[] submit;
		public final String[] delete;
		public final String[] deleteForce;
		public final String regex;
		public final String template;

		public HPCDefinition(String name, String[] submit, String[] delete, String[] deleteForce, String regex, String template) {
			this.name = name;
			this.submit = submit;
			this.delete = delete;
			this.deleteForce = deleteForce;
			this.regex = regex;
			this.template = template;
		}

		public JsonObject toJson() {
			return Json.createObjectBuilder()
					.add("submit", Json.createArrayBuilder(List.of(submit)))
					.add("delete", Json.createArrayBuilder(List.of(delete)))
					.add("delete_force", Json.createArrayBuilder(List.of(deleteForce)))
					.add("regex", regex)
					.add("template", template)
					.build();
		}
	}

	private static JsonObject SCHEMA_HPC_DEFINITION;

	static {
		try(InputStream is = HPCActuator.class.getResourceAsStream("hpc_definition.json")) {
			if(is == null) {
				throw new IOException("Internal schema doesn't exist. This is a bug.");
			}
			SCHEMA_HPC_DEFINITION = Json.createReader(is).readObject();
		} catch(IOException e) {
			throw new RuntimeException("Unable to load hpc_definition.json, this is a bug.");
		}
	}

	private static HPCDefinition parseHpcDef(String name, JsonObject jo, boolean validate) throws IOException {
		String template;
		if(jo.containsKey("template")) {
			template = jo.getString("template");
		} else if(jo.containsKey("template_file")) {
			template = new String(Files.readAllBytes(Paths.get(jo.getString("template_file"))), StandardCharsets.UTF_8);
		} else if(jo.containsKey("template_classpath")) {
			try(InputStream is = HPCResourceType.class.getClassLoader().getResourceAsStream(jo.getString("template_classpath"))) {
				if(is == null) {
					throw new IOException("No such template in classpath");
				}
				template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		} else {
			/* Should never get here. */
			throw new IllegalStateException();
		}

		assert template != null;

		String _regex = jo.getString("regex");
		/* Do a dummy render and attempt to compile the regex. */
		if(validate) {
			Jinjava jj = HPCActuator.createTemplateEngine();
			Map<String, Object> vars = HPCActuator.createSampleVars();
			jj.render(template, vars);

			Pattern.compile(_regex);
		}

		return new HPCDefinition(
				name,
				jo.getJsonArray("submit").stream().map(jv -> ((JsonString)jv).getString()).toArray(String[]::new),
				jo.getJsonArray("delete").stream().map(jv -> ((JsonString)jv).getString()).toArray(String[]::new),
				jo.getJsonArray("delete_force").stream().map(jv -> ((JsonString)jv).getString()).toArray(String[]::new),
				_regex,
				template
		);
	}

	public static Map<String, HPCDefinition> loadConfig(Path[] configDirs, List<String> errors) throws IOException {
		JsonObject internalConfig;
		try(InputStream is = HPCActuator.class.getResourceAsStream("hpc.json")) {
			internalConfig = Json.createReader(is).readObject();
		}

		if(!ActuatorUtils.validateAgainstSchemaStandalone(SCHEMA_HPC_DEFINITION, internalConfig, errors)) {
			throw new RuntimeException("Invalid internal HPC configuration, this is a bug");
		}

		JsonObjectBuilder job = Json.createObjectBuilder(internalConfig);

		{
			List<Path> confDirs = new ArrayList<>();
			confDirs.addAll(Arrays.asList(configDirs));
			Collections.reverse(confDirs);

			for(Path p : confDirs) {
				p = p.resolve("hpc.json");
				if(!Files.exists(p)) {
					continue;
				}

				JsonObject jo;
				try(InputStream is = Files.newInputStream(p)) {
					jo = Json.createReader(is).readObject();
				}

				if(!ActuatorUtils.validateAgainstSchemaStandalone(SCHEMA_HPC_DEFINITION, jo, errors)) {
					throw new IOException(String.format("File %s is malformed", p));
				}
				job.addAll(Json.createObjectBuilder(jo));
			}
		}

		return job.build().entrySet().stream().collect(Collectors.toMap(
				e -> e.getKey(),
				e -> {
					try {
						return parseHpcDef(e.getKey(), e.getValue().asJsonObject(), true);
					} catch(IOException ee) {
						throw new UncheckedIOException(ee);
					}
				}
		));
	}
}
