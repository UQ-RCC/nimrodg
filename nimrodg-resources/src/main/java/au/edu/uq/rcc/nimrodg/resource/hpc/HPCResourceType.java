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
package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HPCResourceType extends SSHResourceType {

	public HPCResourceType() {
		super("hpc", "HPC");
	}

	@Override
	protected void addArguments(ArgumentParser argparser) {
		super.addArguments(argparser);

		argparser.addArgument("--limit")
				.type(Integer.class)
				.help("The node's agent limit.")
				.required(true);

		argparser.addArgument("--tmpvar")
				.type(String.class)
				.help("The environment variable that contains the working directory of the job.")
				.setDefault("TMPDIR");

		argparser.addArgument("--max-batch-size")
				.dest("max_batch_size")
				.type(Integer.class)
				.help("The maximum size of a batch of agents.")
				.setDefault(10);

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
				.help("Walltime used by each batch (supports HH[:MM[:SS]] and [Hh][Mm][Ss])");

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
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		jb.add("tmpvar", ns.getString("tmpvar"));

		Integer limit = ns.getInt("limit");
		if(limit != null) {
			jb.add("limit", limit);
		}

		jb.add("max_batch_size", ns.getInt("max_batch_size"));

		Map<String, HPCDefinition> hpcDefs;

		try {
			hpcDefs = loadConfig(configDirs, new ArrayList<>());
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		HPCDefinition hpc = hpcDefs.get(ns.getString("type"));
		if(hpc == null) {
			err.printf("Unknown type, valid options are: [%s]\n", String.join(", ", hpcDefs.keySet()));
			return false;
		}

		valid = validateTemplate(hpc.submitTemplate, err) && valid;

		if(valid) {
			jb.add("definition", hpc.toJson());
		}

		long ncpus = ns.getLong("ncpus");
		long mem = StringUtils.parseMemory(ns.getString("mem"));
		long walltime = StringUtils.parseWalltime(ns.getString("walltime"));

		if(ncpus < 1 || mem < 1 || walltime < 1) {
			err.print("ncpus, mem, and walltime cannot be < 1\n");
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
	protected String getConfigSchema() {
		return "resource_cluster_hpc.json";
	}

	@Override
	protected Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshCfg) throws IOException {
		JsonObject cfg = node.getConfig().asJsonObject();
		return new HPCActuator(ops, node, amqpUri, certs, new HPCConfig(
				sshCfg,
				cfg.getInt("limit"),
				cfg.getString("tmpvar", "TMPDIR"),
				cfg.getInt("max_batch_size"),
				cfg.getJsonNumber("ncpus").longValue(),
				cfg.getJsonNumber("mem").longValue(),
				cfg.getJsonNumber("walltime").longValue(),
				cfg.getString("account", null),
				cfg.getString("queue", null),
				cfg.getString("server", null),
				HPCDefinition.fromJson("", cfg.getJsonObject("definition"), true)
		));
	}

	private static boolean validateTemplate(String template, PrintStream err) {
		/* Do a dummy render to see if the user's messed up. */
		try {
			HPCActuator.renderTemplate(template, HPCActuator.TEMPLATE_SAMPLE_VARS);
		} catch(RuntimeException e) {
			err.print("Malformed template.\n");
			e.printStackTrace(err);
			return false;
		}

		return true;
	}

	public static Map<String, HPCDefinition> loadConfig(Path[] configDirs, List<String> errors) throws IOException {
		JsonObjectBuilder job = Json.createObjectBuilder();

		{
			List<Path> confDirs = new ArrayList<>(Arrays.asList(configDirs));
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

				if(!ActuatorUtils.validateAgainstSchemaStandalone(HPCDefinition.JSON_SCHEMA, jo, errors)) {
					throw new IOException(String.format("File %s is malformed", p));
				}
				job.addAll(Json.createObjectBuilder(jo));
			}
		}

		/* Merge old and the new. */
		Map<String, HPCDefinition> defs = new HashMap<>(HPCDefinition.INBUILT_DEFINITIONS);
		job.build().forEach((k, v) -> {
			try {
				defs.put(k, HPCDefinition.fromJson(k, v.asJsonObject(), true));
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		return defs;
	}

}
