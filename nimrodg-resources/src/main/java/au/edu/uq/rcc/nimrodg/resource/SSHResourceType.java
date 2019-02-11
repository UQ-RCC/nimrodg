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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.SystemInformation;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.ssh.OpenSSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHTunnel;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import java.util.Optional;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.FeatureControl;

public abstract class SSHResourceType extends BaseResourceType {

	private static final String DEFAULT_SCHEMA = "resource_ssh.json";

	public final String name;
	public final String displayName;

	protected SSHResourceType(String name, String displayName) {
		this.name = name;
		this.displayName = displayName;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, jb);

		boolean noValidatePrivateKey = ns.getBoolean("no_validate_private_key");

		/* We need to do some pre-validation here so we can get the host key and/or platform */
		String hostKey = ns.getString("hostkey");
		boolean detectHostKey = hostKey == null || hostKey.equals("autodetect");

		String platform = ns.getString("platform");
		boolean detectPlatform = platform == null || platform.equals("autodetect");

		List<String> errors = new ArrayList<>();
		URI uri = ActuatorUtils.validateUriString(ns.getString("uri"), errors);
		if(uri == null) {
			errors.forEach(e -> err.println(e));
			return false;
		}

		/* This is checked by validateUri. */
		Optional<String> user = ActuatorUtils.getUriUser(uri);

		String transport = ns.getString("transport");
		TransportFactory tf = createTransportFactory(transport);
		if(tf == null) {
			err.printf("Unknown transport backend %s.\n", transport);
			return false;
		}

		Optional<Path> keyFile = Optional.ofNullable(ns.getString("key")).map(s -> Paths.get(s));

		/* Always load the key here to see if it's valid. */
		Optional<KeyPair> kp = Optional.empty();
		if(!noValidatePrivateKey && keyFile.isPresent()) {
			try {
				kp = Optional.of(ActuatorUtils.readPEMKey(keyFile.get()));
			} catch(IOException e) {
				err.printf("Unable to read private key.\n");
				e.printStackTrace(err);
				valid = false;
			}
		}

		Optional<PublicKey> hkk = Optional.empty();
		if(!detectHostKey) {
			AuthorizedKeyEntry ak = AuthorizedKeyEntry.parseAuthorizedKeyEntry(hostKey);
			try {
				hkk = Optional.of(ak.resolvePublicKey(PublicKeyEntryResolver.FAILING));
			} catch(IOException | GeneralSecurityException e) {
				e.printStackTrace(err);
				valid = false;
			}
		}

		TransportFactory.Config rawConfig = new TransportFactory.Config(
				uri,
				user,
				hkk.map(k -> new PublicKey[]{k}).orElse(new PublicKey[0]),
				keyFile,
				kp,
				Optional.ofNullable(ns.getString("openssh_executable")).map(s -> Paths.get(s)),
				new SSHTunnel[0]
		);

		TransportFactory.Config cfg;
		try {
			cfg = tf.resolveConfiguration(rawConfig);
		} catch(IOException e) {
			e.printStackTrace(err);
			return false;
		}

		if(detectPlatform) {
			try(RemoteShell shell = tf.create(cfg)) {
				SystemInformation sysInfo = SystemInformation.getSystemInformation(shell);
				AgentInfo agent = ap.lookupAgentByPosix(sysInfo.kernelName, sysInfo.machine);
				if(agent == null) {
					err.printf("No supported agent for machine tuple (%s, %s)", sysInfo.kernelName, sysInfo.machine);
					return false;
				}
				platform = agent.getPlatformString();
			} catch(IOException | IllegalArgumentException e) {
				e.printStackTrace(err);
				return false;
			}
		}

		jb.add("agent_platform", platform);
		jb.add("transport", tf.buildJsonConfiguration(cfg));

		return valid;
	}

	@Override
	protected void addArguments(ArgumentParser parser) {

		parser.addArgument("--platform")
				.dest("platform")
				.type(String.class)
				.help("Agent Platform String. Omit or set to 'autodetect' to retrieve it now.")
				.setDefault("autodetect")
				.required(false);

		parser.addArgument("--transport")
				.dest("transport")
				.type(String.class)
				.choices("sshd", "openssh")
				.setDefault("sshd");

		parser.addArgument("--uri")
				.dest("uri")
				.type(String.class)
				.help("SSH URI")
				.required(true);

		parser.addArgument("--key")
				.dest("key")
				.type(String.class)
				.help("SSH Private Key")
				.required(false);

		parser.addArgument("--hostkey")
				.dest("hostkey")
				.type(String.class)
				.help("SSH Host Key. Omit or set to 'autodetect' to retrieve it now. Ignored if not using the SSH transport.")
				.setDefault("autodetect");

		parser.addArgument("--openssh-executable")
				.dest("openssh_executable")
				.type(String.class)
				.help("Path to the OpenSSH executable. Ignored if not using OpenSSH transport.")
				.setDefault("ssh");

		parser.addArgument("--tunnel-local")
				.dest("tunnel")
				.help("")
				.nargs("*");

		/* Hidden argument mainly used for testing. Don't attempt to load the private key if set. */
		parser.addArgument("--no-validate-private-key")
				.dest("no_validate_private_key")
				.help(FeatureControl.SUPPRESS)
				.action(Arguments.storeTrue());
	}

	@Override
	protected String getConfigSchema() {
		return DEFAULT_SCHEMA;
	}

	private static TransportFactory createTransportFactory(String name) {
		switch(name) {
			case SSHClient.TRANSPORT_NAME:
				return SSHClient.FACTORY;
			case OpenSSHClient.TRANSPORT_NAME:
				return OpenSSHClient.FACTORY;
		}

		return null;
	}

	@Override
	protected boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
		if(!super.validateConfiguration(ap, _cfg, errors)) {
			return false;
		}

		boolean valid = true;
		JsonObject cfg = _cfg.asJsonObject();

		JsonObject transportCfg = cfg.getJsonObject("transport");

		String transportName = transportCfg.getString("name");
		TransportFactory tf = createTransportFactory(transportName);
		if(tf == null) {
			errors.add(String.format("Unknown transport backend %s.\n", transportName));
			return false;
		}

		if(!tf.validateConfiguration(transportCfg, errors).isPresent()) {
			valid = false;
		}

		if(!ActuatorUtils.lookupAgentByPlatform(ap, cfg.getString("agent_platform"), errors).isPresent()) {
			valid = false;
		}

		return valid;
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs) throws IOException {
		List<String> errors = new ArrayList<>();

		JsonStructure _cfg = node.getConfig();
		if(!validateConfiguration(ops.getNimrod(), _cfg, errors)) {
			throw new IOException(errors.get(0));
		}

		JsonObject cfg = _cfg.asJsonObject();

		JsonObject tcfg = cfg.getJsonObject("transport");
		TransportFactory tf = createTransportFactory(tcfg.getString("name"));
		if(tf == null) {
			throw new IOException("Unable to create transport");
		}

		Optional<TransportFactory.Config> transConfig = tf.validateConfiguration(tcfg, errors);
		if(!transConfig.isPresent()) {
			throw new IOException(errors.get(0));
		}

		Optional<AgentInfo> ai = ActuatorUtils.lookupAgentByPlatform(ops.getNimrod(), cfg.getString("agent_platform"), errors);
		if(!ai.isPresent()) {
			throw new IOException(errors.get(0));
		}

		return createActuator(ops, node, amqpUri, certs, new SSHConfig(ai.get(), tf, transConfig.get()));

	}

	protected abstract Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshConfig) throws IOException;

	public static class SSHConfig {

		public final AgentInfo agentInfo;
		public final TransportFactory transportFactory;
		public final TransportFactory.Config transportConfig;

		public SSHConfig(AgentInfo ai, TransportFactory transportFactory, TransportFactory.Config transportConfig) {
			this.agentInfo = ai;
			this.transportFactory = transportFactory;
			this.transportConfig = transportConfig;
		}

		protected SSHConfig(SSHConfig cfg) {
			this(cfg.agentInfo, cfg.transportFactory, cfg.transportConfig);
		}
	}
}
