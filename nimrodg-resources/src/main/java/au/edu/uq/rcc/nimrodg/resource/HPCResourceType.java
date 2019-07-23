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
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class HPCResourceType extends ClusterResourceType {

	private Map<String, HPCDefinition> hpcDefs;

	public HPCResourceType() {
		super("hpc", "HPC", "hpcargs");
		this.hpcDefs = null;
	}

	@Override
	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {
		super.buildParserBeforeSubmissionArgs(argparser);

		if(hpcDefs == null) {
			try {
				hpcDefs = loadConfig(new ArrayList<>());
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		argparser.addArgument("--type")
				.dest("type")
				.type(String.class)
				.required(true)
				.choices(hpcDefs.keySet())
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
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, jb);

		assert hpcDefs != null;
		HPCDefinition hpc = hpcDefs.get(ns.getString("type"));
		assert hpc != null;

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

//		Optional<String> template;
//		String templatePath = ns.getString("template");
//		try {
//			if(templatePath != null) {
//				template = Optional.of(new String(Files.readAllBytes(Paths.get(templatePath)), StandardCharsets.UTF_8));;
//			} else {
//				template = hpc.template;
//			}
//		} catch(IOException e) {
//			err.printf("Malformed template.\n");
//			e.printStackTrace(err);
//			template = Optional.empty();
//		}
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
				parseHpcDef("", ccfg.getJsonObject("definition"), true)
		));
	}

	public static class HPCConfig extends ClusterConfig {

		public final long ncpus;
		public final long mem;
		public final long walltime;
		public final HPCDefinition hpc;

		public HPCConfig(ClusterConfig cfg, long ncpus, long mem, long walltime, HPCDefinition hpc) {
			super(cfg);
			this.ncpus = ncpus;
			this.mem = mem;
			this.walltime = walltime;
			this.hpc = hpc;
		}

		public HPCConfig(HPCConfig cfg) {
			this(cfg, cfg.ncpus, cfg.mem, cfg.walltime, cfg.hpc);
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

	public static Map<String, HPCDefinition> loadConfig(List<String> errors) throws IOException {
		JsonObject internalConfig;
		try(InputStream is = HPCActuator.class.getResourceAsStream("hpc.json")) {
			internalConfig = Json.createReader(is).readObject();
		}

		if(!ActuatorUtils.validateAgainstSchemaStandalone(SCHEMA_HPC_DEFINITION, internalConfig, errors)) {
			throw new RuntimeException("Invalid internal HPC configuration, this is a bug.");
		}

		// TODO: Load sysadmin and user config
		return internalConfig.entrySet().stream().collect(Collectors.toMap(
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
