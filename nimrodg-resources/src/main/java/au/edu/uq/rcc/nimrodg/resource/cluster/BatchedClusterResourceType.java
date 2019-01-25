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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import au.edu.uq.rcc.nimrodg.api.Resource;

public abstract class BatchedClusterResourceType extends ClusterResourceType {

	protected static final Pattern BATCH_RESOURCE_PATTERN = Pattern.compile("^([\\w-]+):(.+)$");

	protected final BatchDialect dialect;

	public BatchedClusterResourceType(String name, String displayName, String argsName, BatchDialect dialect) {
		super(name, displayName, argsName);
		this.dialect = dialect;
	}

	@Override
	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {
		super.buildParserBeforeSubmissionArgs(argparser);
		argparser.addArgument("--max-batch-size")
				.dest("max_batch_size")
				.type(Integer.class)
				.help("The maximum size of a batch of agents.")
				.setDefault(10);

		argparser.addArgument("--add-batch-res-static")
				.dest("batch_resource_static")
				.type(String.class)
				.action(Arguments.append())
				.help("Add a static batch resource.");

		argparser.addArgument("--add-batch-res-scale")
				.dest("batch_resource_scale")
				.type(String.class)
				.action(Arguments.append())
				.help("Add a scalable batch resource.");
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, jb);

		jb.add("max_batch_size", ns.getInt("max_batch_size"));

		List<BatchDialect.Resource> staticResources = new ArrayList<>();
		List<String> resList = ns.getList("batch_resource_static");
		if(resList != null) {
			for(String s : resList) {
				valid = parseBatchResource(s, err, staticResources) && valid;
			}
		}

		List<BatchDialect.Resource> scaleResources = new ArrayList<>();
		resList = ns.getList("batch_resource_scale");
		if(resList != null) {
			for(String s : resList) {
				valid = parseBatchResource(s, err, scaleResources) && valid;
			}
		}

		JsonArrayBuilder jao = Json.createArrayBuilder();
		valid = dialect.parseResources(
				scaleResources.stream().toArray(BatchDialect.Resource[]::new),
				staticResources.stream().toArray(BatchDialect.Resource[]::new),
				out,
				err,
				jao
		) && valid;

		jb.add("batch_config", jao);
		return valid;
	}

	private static boolean parseBatchResource(String s, PrintStream err, List<BatchDialect.Resource> res) {
		Matcher m = BATCH_RESOURCE_PATTERN.matcher(s);
		if(!m.matches()) {
			err.printf("Malformed batch static resource specification. Must match pattern %s\n", BATCH_RESOURCE_PATTERN.pattern());
			return false;
		}

		res.add(new BatchDialect.Resource(m.group(1), m.group(2)));
		return true;
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cluster_batched.json";
	}

	@Override
	protected final Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig ccfg) throws IOException {
		JsonObject cfg = node.getConfig().asJsonObject();
		return createActuator(ops, node, amqpUri, certs, new BatchedClusterConfig(
				ccfg,
				cfg.getInt("max_batch_size"),
				dialect,
				cfg.getJsonArray("batch_config").stream().map(v -> v.asJsonObject()).toArray(JsonObject[]::new)
		));
	}

	protected abstract Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, BatchedClusterConfig ccfg) throws IOException;

	public static class BatchedClusterConfig extends ClusterConfig {

		public final int maxBatchSize;
		public final BatchDialect dialect;
		public final JsonObject[] batchConfig;

		public BatchedClusterConfig(ClusterConfig cfg, int maxBatchSize, BatchDialect dialect, JsonObject[] batchConfig) {
			super(cfg);
			this.maxBatchSize = maxBatchSize;
			this.dialect = dialect;
			this.batchConfig = Arrays.copyOf(batchConfig, batchConfig.length);
		}

		public BatchedClusterConfig(BatchedClusterConfig cfg) {
			this(cfg, cfg.maxBatchSize, cfg.dialect, cfg.batchConfig);
		}
	}
}
