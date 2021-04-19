/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class RemoteResourceType extends SSHResourceType {

	public RemoteResourceType() {
		super("remote", "Remote");
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);
		jb.add("limit", ns.getInt("limit"));
		jb.add("tmpdir", ns.getString("tmpdir"));
		return valid;
	}


	@Override
	protected void addArguments(ArgumentParser parser) {
		super.addArguments(parser);

		parser.addArgument("--limit")
				.dest("limit")
				.type(Integer.class)
				.help("The agent limit. Omit for no limit.")
				.setDefault(Integer.MAX_VALUE);

		parser.addArgument("--tmpdir")
				.dest("tmpdir")
				.type(String.class)
				.help("The temporary directory. Defaults to '/tmp'.")
				.setDefault("/tmp");
	}

	@Override
	protected String getConfigSchema() {
		return "resource_remotessh.json";
	}

	@Override
	protected Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshCfg) throws IOException {
		JsonObject cfg = node.getConfig().asJsonObject();
		return new RemoteActuator(ops, node, amqpUri, certs, cfg.getInt("limit"), cfg.getString("tmpdir"), sshCfg);
	}

}
