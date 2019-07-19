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
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType;
import au.edu.uq.rcc.nimrodg.resource.cluster.HPCActuator;
import com.hubspot.jinjava.Jinjava;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class HPCResourceType extends ClusterResourceType {

	public HPCResourceType() {
		super("hpc", "HPC", "hpcargs");
	}

	@Override
	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {
		super.buildParserBeforeSubmissionArgs(argparser);

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

		argparser.addArgument("--template")
				.dest("template")
				.type(String.class)
				.required(true)
				.help("Submission Template File");
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cluster_hpc.json";
	}

	private static Optional<String> loadAndValidateTemplate(Path path, PrintStream out, PrintStream err) {
		byte[] raw;
		try {
			raw = Files.readAllBytes(path);
		} catch(IOException e) {
			err.printf("Unable to load submission template.\n");
			e.printStackTrace(err);
			return Optional.empty();
		}

		String template = new String(raw, StandardCharsets.UTF_8);

		/* Do a dummy render to see if the user's messed up. */
		Jinjava jj = HPCActuator.createTemplateEngine();
		Map<String, Object> vars = HPCActuator.createSampleVars();
		try {
			jj.render(template, vars);
		} catch(RuntimeException e) {
			err.printf("Malformed template.\n");
			e.printStackTrace(err);
			return Optional.empty();
		}
		return Optional.of(template);
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, jb);

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

		Optional<String> template = loadAndValidateTemplate(Paths.get(ns.getString("template")), out, err);
		valid = template.isPresent() && valid;
		template.ifPresent(t -> jb.add("template", t));

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
				// FIXME:
				new String[]{"qsub"},
				new String[]{"qdel"},
				new String[]{"qdel", "-W", "force"},
				ccfg.getString("template")
		));
	}

	public static class HPCConfig extends ClusterConfig {

		public final long ncpus;
		public final long mem;
		public final long walltime;
		public final String[] submit;
		public final String[] delete;
		public final String[] forceDelete;
		public final String template;

		public HPCConfig(ClusterConfig cfg, long ncpus, long mem, long walltime, String[] submit, String[] delete, String[] forceDelete, String template) {
			super(cfg);
			this.ncpus = ncpus;
			this.mem = mem;
			this.walltime = walltime;
			this.submit = Arrays.copyOf(submit, submit.length);
			this.delete = Arrays.copyOf(delete, delete.length);
			this.forceDelete = Arrays.copyOf(forceDelete, forceDelete.length);
			this.template = template;
		}

		public HPCConfig(HPCConfig cfg) {
			this(cfg, cfg.ncpus, cfg.mem, cfg.walltime, cfg.submit, cfg.delete, cfg.forceDelete, cfg.template);
		}
	}

	public static class HPCDefinition {

		public final String name;
		public final String[] submit;
		public final String[] delete;
		public final String[] deleteForce;
		public final String regex;

		public HPCDefinition(String name, String[] submit, String[] delete, String[] deleteForce, String regex) {
			this.name = name;
			this.submit = submit;
			this.delete = delete;
			this.deleteForce = deleteForce;
			this.regex = regex;
		}
	}

	public static final Map<String, HPCDefinition> asdfasdf = Map.of(
			"pbspro", new HPCDefinition("pbspro", new String[]{"qsub"}, new String[]{"qdel"}, new String[]{"qdel", "-W", "force"}, "^(.+)$")
	);
}
