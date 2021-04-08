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
import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.cloud.JcloudsActuator;
import au.edu.uq.rcc.nimrodg.resource.cloud.JcloudsActuator.CloudConfig;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonStructure;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;

public class CloudResourceType extends BaseResourceType {

	@Override
	public String getName() {
		return "cloud";
	}

	@Override
	public String getDisplayName() {
		return "Cloud";
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cloud.json";
	}

	@Override
	protected void addArguments(ArgumentParser parser) {
		super.addArguments(parser);

		parser.addArgument("--agents-per-node")
				.type(Integer.class)
				.dest("agents_per_node")
				.help("Number of agents per node.")
				.required(true);

		parser.addArgument("--platform")
				.type(String.class)
				.help("Agent Platform String.")
				.required(true);

		parser.addArgument("--tmpdir")
				.type(String.class)
				.help("Node temporary directory.")
				.setDefault("/tmp");

		parser.addArgument("--context")
				.help("JClouds context provider name.")
				.type(String.class)
				.required(true);

		parser.addArgument("--endpoint")
				.type(String.class)
				.required(true);

		parser.addArgument("--username")
				.type(String.class)
				.required(true);

		parser.addArgument("--password")
				.type(String.class)
				.required(true);

		parser.addArgument("--location-id")
				.dest("location_id")
				.type(String.class)
				.help("The region or datacenter in which your node(s) should run.")
				.required(false);

		MutuallyExclusiveGroup hwg = parser.addMutuallyExclusiveGroup();
		hwg.addArgument("--hardware-id")
				.dest("hardware_id")
				.type(String.class)
				.required(false);
		hwg.addArgument("--hardware")
				.type(String.class)
				.required(false);

		parser.addArgument("--image-id")
				.dest("image_id")
				.type(String.class)
				.required(true);

		parser.addArgument("--availability-zone")
				.type(String.class)
				.help("Availability Zone, only supported on some providers.")
				.required(false);

		parser.addArgument("-D")
				.type(String.class)
				.help("JClouds context override properties. These are passed directly to ContextBuilder#overrides().")
				.dest("properties")
				.action(Arguments.append());
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		System.out.println(ns);
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		Properties ppp = new Properties();
		List<String> plist = ns.getList("properties");
		if(plist != null) {
			for(String s : plist) {
				try {
					ppp.load(new StringReader(s));
				} catch(IOException e) {
					valid = false;
					err.printf("%s\n", e.getMessage());
				}
			}

		}

		JsonObjectBuilder pb = Json.createObjectBuilder();
		ppp.entrySet().stream().forEach(e -> pb.add((String)e.getKey(), (String)e.getValue()));

		jb.add("agents_per_node", ns.getInt("agents_per_node"));
		jb.add("agent_platform", ns.getString("platform"));
		jb.add("tmpdir", ns.getString("tmpdir"));

		String context = ns.getString("context");
		String endpoint = ns.getString("endpoint");
		String username = ns.getString("username");
		String password = ns.getString("password");

		jb.add("context", context);
		jb.add("endpoint", endpoint);
		jb.add("username", username);
		jb.add("password", password);
		jb.add("location_id", ns.getString("location_id"));
		jb.add("properties", pb);

		ComputeService cs = ContextBuilder.newBuilder(context)
				.endpoint(endpoint)
				.credentials(username, password)
				.overrides(ppp)
				.buildView(ComputeServiceContext.class)
				.getComputeService();

		Optional<Hardware> hw = Optional.ofNullable(ns.getString("hardware_id"))
				.flatMap(id -> cs.listHardwareProfiles().stream().filter(h -> id.equals(h.getId())).findFirst().map(h -> (Hardware)h))
				.or(() -> Optional.ofNullable(ns.getString("hardware"))
				.flatMap(name -> cs.listHardwareProfiles().stream().filter(h -> name.equals(h.getName())).findFirst()));

		hw.ifPresent(h -> jb.add("hardware_id", h.getId()));
		if(hw.isEmpty()) {
			valid = false;
			err.println("No such hardware.");
		}

		Image img = cs.getImage(ns.getString("image_id"));
		if(img == null) {
			valid = false;
			err.println("No such image.");
		} else {
			jb.add("image_id", img.getId());
		}

		if(!hw.map(h -> h.supportsImage().apply(img)).orElse(true)) {
			valid = false;
			err.println("Hardware doesn't support image.");
		}

		jb.add("availability_zone", Optional.ofNullable(ns.getString("availability_zone")).orElse(""));

		return valid;
	}

	@Override
	public boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
		if(!super.validateConfiguration(ap, _cfg, errors)) {
			return false;
		}

		JsonObject jo = _cfg.asJsonObject();
		String platform = jo.getString("agent_platform");
		if(ap.lookupAgentByPlatform(platform) == null) {
			errors.add("No agents match provided platform.");
			return false;
		}

		return true;
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs) throws IOException {
		List<String> errors = new ArrayList<>();
		JsonStructure _cfg = node.getConfig();
		if(!validateConfiguration(ops, _cfg, errors)) {
			throw new IOException("Invalid resource configuration");
		}

		JsonObject cfg = _cfg.asJsonObject();

		Properties props = new Properties();
		cfg.getJsonObject("properties").forEach((k, v) -> props.put(k, ((JsonString)v).getString()));

		AgentDefinition agentDef = ops.lookupAgentByPlatform(cfg.getString("agent_platform"));

		return new JcloudsActuator(ops, node, amqpUri, certs, agentDef, cfg.getInt("agents_per_node"), cfg.getString("tmpdir"), new CloudConfig(
				cfg.getString("context"),
				URI.create(cfg.getString("endpoint")),
				cfg.getString("location_id"),
				cfg.getString("hardware_id"),
				cfg.getString("image_id"),
				cfg.getString("username"),
				cfg.getString("password"),
				cfg.getString("availability_zone"),
				props
		));
	}
}
