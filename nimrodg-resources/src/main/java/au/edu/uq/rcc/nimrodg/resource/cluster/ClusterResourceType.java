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
package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import java.io.IOException;
import java.io.PrintStream;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import java.nio.file.Path;
import javax.json.JsonArray;
import javax.json.JsonString;
import javax.json.JsonStructure;

public abstract class ClusterResourceType extends SSHResourceType {

	public final String argsName;

	public ClusterResourceType(String name, String displayName, String argsName) {
		super(name, displayName);
		this.argsName = argsName;
	}

	protected boolean validateSubmissionArgs(JsonArray ja, List<String> errors) {
		return true;
	}

	@Override
	protected boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
		boolean valid = super.validateConfiguration(ap, _cfg, errors);
		return validateSubmissionArgs(_cfg.asJsonObject().getJsonArray(argsName), errors) && valid;
	}

	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {

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

		this.buildParserBeforeSubmissionArgs(argparser);

		argparser.addArgument(argsName)
				.help(String.format("%s submission arguments.", displayName))
				.nargs("*");
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		jb.add("tmpvar", ns.getString("tmpvar"));
		jb.add(argsName, Json.createArrayBuilder(ns.getList(argsName)).build());

		Integer limit = ns.getInt("limit");
		if(limit != null) {
			jb.add("limit", limit);
		}

		jb.add("max_batch_size", ns.getInt("max_batch_size"));
		return valid;
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cluster.json";
	}

	@Override
	protected Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshCfg) throws IOException {
		JsonObject cfg = node.getConfig().asJsonObject();
		JsonString _tmpVar = cfg.getJsonString("tmpvar");
		String tmpVar = _tmpVar == null ? "TMPDIR" : _tmpVar.getString();

		return createActuator(ops, node, amqpUri, certs, new ClusterConfig(
				sshCfg,
				cfg.getInt("limit"),
				tmpVar,
				cfg.getJsonArray(argsName).stream().map(a -> ((JsonString)a).getString()).toArray(String[]::new),
				cfg.getInt("max_batch_size")
		));
	}

	protected abstract Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig ccfg) throws IOException;

	public static class ClusterConfig extends SSHConfig {

		public final int limit;
		public final String tmpVar;
		public final String[] submissionArgs;
		public final int maxBatchSize;

		public ClusterConfig(SSHConfig ssh, int limit, String tmpVar, String[] submissionArgs, int maxBatchSize) {
			super(ssh);
			this.limit = limit;
			this.tmpVar = tmpVar;
			this.submissionArgs = Arrays.copyOf(submissionArgs, submissionArgs.length);
			this.maxBatchSize = maxBatchSize;
		}

		public ClusterConfig(ClusterConfig cfg) {
			this(cfg, cfg.limit, cfg.tmpVar, cfg.submissionArgs, cfg.maxBatchSize);
		}
	}
}
