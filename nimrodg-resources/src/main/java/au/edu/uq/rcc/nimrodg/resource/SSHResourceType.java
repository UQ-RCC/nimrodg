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
import au.edu.uq.rcc.nimrodg.resource.ssh.ClientFactories;
import au.edu.uq.rcc.nimrodg.resource.ssh.SystemInformation;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonStructure;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.FeatureControl;

public abstract class SSHResourceType extends BaseResourceType {

	private static final String DEFAULT_SCHEMA = "resource_ssh.json";

	private final String name;
	private final String displayName;

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
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		boolean noValidatePrivateKey = ns.getBoolean("no_validate_private_key");

		/* We need to do some pre-validation here so we can get the host key and/or platform */
		String hostKey = ns.getString("hostkey");
		boolean detectHostKey = hostKey == null || hostKey.equals("autodetect");

		String platform = ns.getString("platform");
		boolean detectPlatform = platform == null || platform.equals("autodetect");

		List<String> errors = new ArrayList<>();
		Optional<URI> uri = Optional.ofNullable(ns.getString("uri")).map(u -> ActuatorUtils.validateUriString(u, errors));

		if(!errors.isEmpty()) {
			errors.forEach(err::println);
			return false;
		}

		/* This is checked by validateUri. */
		Optional<String> user = ShellUtils.getUriUser(uri);

		String transport = ns.getString("transport");
		TransportFactory tf = createTransportFactory(transport);
		if(tf == null) {
			err.printf("Unknown transport backend %s.\n", transport);
			return false;
		}

		Optional<Path> keyFile = Optional.ofNullable(ns.getString("key"))
				.map(URI::create)
				.map(u -> {
					if(u.getScheme() == null) {
						return Paths.get(u.toString());
					} else {
						return Paths.get(u);
					}
				});

		/* Always load the key here to see if it's valid. */
		if(!noValidatePrivateKey && keyFile.isPresent()) {
			try {
				ActuatorUtils.readPEMKey(keyFile.get());
			} catch(IOException e) {
				err.println("Unable to read private key.");
				e.printStackTrace(err);
				valid = false;
			}
		}

		Optional<PublicKey> hkk = Optional.empty();
		if(!detectHostKey) {
			try {
				hkk = Optional.of(ShellUtils.parseAuthorizedKeyEntry(hostKey));
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
				Optional.ofNullable(ns.getString("openssh_executable")).map(s -> Paths.get(s))
		);

		TransportFactory.Config cfg;
		try {
			cfg = tf.resolveConfiguration(rawConfig);
		} catch(IOException e) {
			e.printStackTrace(err);
			return false;
		}

		if(detectPlatform) {
			try {
				platform = detectPlatform(ap, tf, cfg);
			} catch(IOException | IllegalArgumentException e) {
				e.printStackTrace(err);
				return false;
			}

			if(platform == null) {
				err.println("Unable to detect agent platform");
				return false;
			}
		}

		List<String> envs = ns.getList("forward_env");
		if(envs == null) {
			envs = List.of();
		}

		jb.add("agent_platform", platform);
		jb.add("transport", tf.buildJsonConfiguration(cfg));
		jb.add("forwarded_environment", Json.createArrayBuilder(envs));
		return valid;
	}

	private static String detectPlatform(AgentProvider ap, TransportFactory tf, TransportFactory.Config cfg) throws IOException, IllegalArgumentException {
		Path tmpDir = Files.createTempDirectory(null, PosixFilePermissions.asFileAttribute(EnumSet.of(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE
		)));

		try(RemoteShell shell = tf.create(cfg, tmpDir)) {
			SystemInformation sysInfo = SystemInformation.getSystemInformation(shell);
			AgentInfo agent = ap.lookupAgentByPosix(sysInfo.kernelName, sysInfo.machine);
			if(agent == null) {
				return null;
			}
			return agent.getPlatformString();
		} finally {
			Files.deleteIfExists(tmpDir);
		}
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
				.choices("sshd", "openssh", "local")
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

		parser.addArgument("--forward-env")
				.dest("forward_env")
				.type(String.class)
				.help("Forward an environment variable through to agents spawned on the remote system. Can be specified multiple times.")
				.required(false)
				.action(Arguments.append());

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

	private static final Map<String, TransportFactory> FACTORIES = Map.of(
			ClientFactories.OPENSSH_TRANSPORT_NAME, ClientFactories.OPENSSH_FACTORY,
			ClientFactories.LOCAL_TRANSPORT_NAME, ClientFactories.LOCAL_FACTORY,
			ClientFactories.SSHD_TRANSPORT_NAME, ClientFactories.SSHD_FACTORY
	);

	private static TransportFactory createTransportFactory(String name) {
		return FACTORIES.get(name);
	}

	@Override
	public boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
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
		if(!validateConfiguration(ops, _cfg, errors)) {
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

		Optional<AgentInfo> ai = ActuatorUtils.lookupAgentByPlatform(ops, cfg.getString("agent_platform"), errors);
		if(!ai.isPresent()) {
			throw new IOException(errors.get(0));
		}


		return createActuator(ops, node, amqpUri, certs, new SSHConfig(ai.get(), tf, transConfig.get(), cfg.getJsonArray("forwarded_environment").stream()
				.map(jv -> ((JsonString)jv).getString())
				.collect(Collectors.toList()))
		);
	}

	protected abstract Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshConfig) throws IOException;

	public static class SSHConfig {

		public final AgentInfo agentInfo;
		public final TransportFactory transportFactory;
		public final TransportFactory.Config transportConfig;
		public final List<String> forwardedEnvironment;

		public SSHConfig(AgentInfo ai, TransportFactory transportFactory, TransportFactory.Config transportConfig, List<String> forwardedEnvironment) {
			this.agentInfo = ai;
			this.transportFactory = transportFactory;
			this.transportConfig = transportConfig;
			this.forwardedEnvironment = forwardedEnvironment;
		}

		protected SSHConfig(SSHConfig cfg) {
			this(cfg.agentInfo, cfg.transportFactory, cfg.transportConfig, cfg.forwardedEnvironment);
		}

		protected JsonObjectBuilder toJsonBuilder() {
			JsonArrayBuilder jab = Json.createArrayBuilder();
			forwardedEnvironment.forEach(jab::add);
			return Json.createObjectBuilder()
					.add("agent_platform", agentInfo.getPlatformString())
					.add("transport", transportFactory.buildJsonConfiguration(transportConfig))
					.add("forwarded_environment", jab);
		}

		public final JsonObject toJson() {
			return toJsonBuilder().build();
		}
	}
}
