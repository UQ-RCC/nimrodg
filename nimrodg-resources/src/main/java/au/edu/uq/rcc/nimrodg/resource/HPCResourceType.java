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
				ccfg.getString("template")
		));
	}

	public static class HPCConfig extends ClusterConfig {
		public final String template;

		public HPCConfig(ClusterConfig cfg, String template) {
			super(cfg);
			this.template = template;
		}

		public HPCConfig(HPCConfig cfg) {
			this(cfg, cfg.template);
		}
	}
}
