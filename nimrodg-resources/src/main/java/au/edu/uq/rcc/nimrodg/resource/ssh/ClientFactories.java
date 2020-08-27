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
package au.edu.uq.rcc.nimrodg.resource.ssh;

import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.shell.LocalShell;
import au.edu.uq.rcc.nimrodg.shell.OpenSSHClient;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

public class ClientFactories {

	public static final String LOCAL_TRANSPORT_NAME = "local";
	public static final JsonObject LOCAL_TRANSPORT_SCHEMA = ActuatorUtils.loadInternalSchema(ClientFactories.class, "transport.local.schema.json");
	public static TransportFactory LOCAL_FACTORY = new TransportFactory() {

		@Override
		public RemoteShell create(TransportFactory.Config cfg, Path workDir) {
			return new LocalShell();
		}

		@Override
		public Optional<TransportFactory.Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			Objects.requireNonNull(cfg, "cfg");
			Objects.requireNonNull(errors, "errors");

			if(!ActuatorUtils.validateAgainstSchemaStandalone(LOCAL_TRANSPORT_SCHEMA, cfg, errors)) {
				return Optional.empty();
			}

			return Optional.of(new TransportFactory.Config(
					Optional.empty(),
					new PublicKey[0],
					Optional.empty(),
					Optional.empty()
			));
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) {
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			return Json.createObjectBuilder().add("name", LOCAL_TRANSPORT_NAME).build();
		}
	};

	public static final String OPENSSH_TRANSPORT_NAME = "openssh";
	public static final JsonObject OPENSSH_TRANSPORT_SCHEMA = ActuatorUtils.loadInternalSchema(ClientFactories.class, "transport.openssh.schema.json");
	public static final TransportFactory OPENSSH_FACTORY = new TransportFactory() {

		@Override
		public RemoteShell create(Config cfg, Path workDir) throws IOException {
			if(cfg.uri.isEmpty()) {
				throw new IOException("No URI provided.");
			}

			return new OpenSSHClient(cfg.uri.get(), workDir, cfg.privateKey, cfg.executablePath, Map.of());
		}

		@Override
		public Config resolveConfiguration(Config cfg) throws IOException {
			if(cfg.uri.isEmpty()) {
				throw new IOException("No URI provided.");
			}
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(Config cfg) {
			return Json.createObjectBuilder()
					.add("name", OPENSSH_TRANSPORT_NAME)
					.add("uri", cfg.uri.map(URI::toString).orElse(""))
					.add("keyfile", cfg.privateKey.map(p -> p.toUri().toString()).orElse(""))
					.add("executable", cfg.executablePath.map(Path::toString).orElse(""))
					.build();
		}

		@Override
		public Optional<Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			Objects.requireNonNull(cfg, "cfg");
			Objects.requireNonNull(errors, "errors");

			if(!ActuatorUtils.validateAgainstSchemaStandalone(OPENSSH_TRANSPORT_SCHEMA, cfg, errors)) {
				return Optional.empty();
			}

			boolean valid = true;

			URI uri = ActuatorUtils.validateUriString(cfg.getString("uri"), errors);
			if(uri == null) {
				valid = false;
			}

			if(!valid) {
				return Optional.empty();
			}

			return Optional.of(new Config(
					Optional.of(uri),
					new PublicKey[0],
					TransportFactory.getOrNullIfEmpty(cfg, "keyfile").map(s -> Paths.get(URI.create(s))),
					TransportFactory.getOrNullIfEmpty(cfg, "executable").map(s -> Paths.get(s))
			));
		}
	};

	public static final String SSHD_TRANSPORT_NAME = "sshd";
	public static final JsonObject SSHD_TRANSPORT_SCHEMA = ActuatorUtils.loadInternalSchema(ClientFactories.class, "transport.sshd.schema.json");
	public static TransportFactory SSHD_FACTORY = new TransportFactory() {
		@Override
		public RemoteShell create(TransportFactory.Config cfg, Path workDir) throws IOException {
			if(cfg.uri.isEmpty()) {
				throw new IOException("No URI provided.");
			}

			if(cfg.hostKeys.length == 0) {
				throw new IOException("No host keys provided");
			}

			if(cfg.privateKey.isEmpty()) {
				throw new IOException("No private key provided");
			}

			return new SshdClient(cfg.uri.get(), cfg.hostKeys, ActuatorUtils.readPEMKey(cfg.privateKey.get()));
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) throws IOException {
			if(cfg.uri.isEmpty()) {
				throw new IOException("No URI provided.");
			}

			if(cfg.hostKeys.length > 0) {
				return cfg;
			}

			URI uri = cfg.uri.get();

			int port = uri.getPort();
			if(port <= 0) {
				port = 22;
			}

			PublicKey[] hostKeys = SshdClient.resolveHostKeys(ShellUtils.getUriUser(uri).orElse(""), uri.getHost(), port);
			if(hostKeys.length == 0) {
				throw new IOException("No host keys resolved");
			}

			return new TransportFactory.Config(
					cfg.uri,
					hostKeys,
					cfg.privateKey,
					Optional.empty()
			);
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			JsonArrayBuilder ja = Json.createArrayBuilder();
			for(PublicKey pk : cfg.hostKeys) {
				ja.add(ShellUtils.toAuthorizedKeyEntry(pk));
			}

			return Json.createObjectBuilder()
					.add("name", SSHD_TRANSPORT_NAME)
					.add("uri", cfg.uri.map(URI::toString).orElse(""))
					.add("keyfile", cfg.privateKey.map(p -> p.toUri().toString()).orElse(""))
					.add("hostkeys", ja)
					.build();
		}

		@Override
		public Optional<TransportFactory.Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			Objects.requireNonNull(cfg, "cfg");
			Objects.requireNonNull(errors, "errors");

			if(!ActuatorUtils.validateAgainstSchemaStandalone(SSHD_TRANSPORT_SCHEMA, cfg, errors)) {
				return Optional.empty();
			}

			boolean valid = true;

			URI uri = ActuatorUtils.validateUriString(cfg.getString("uri"), errors);
			if(uri == null) {
				valid = false;
			}

			Optional<String> user = ShellUtils.getUriUser(uri);
			if(user.isEmpty()) {
				errors.add("No user specified.");
				valid = false;
			}

			/* Not going to lie, I got some perverse pleasure from doing this. */
			PublicKey[] hostKeys = Optional.ofNullable(cfg.get("hostkeys"))
					.filter(v -> JsonValue.ValueType.ARRAY.equals(v.getValueType()))
					.map(
							v -> ((JsonArray)v).stream()
									.filter(jv -> JsonValue.ValueType.STRING.equals(jv.getValueType()))
									.map(jv -> ((JsonString)jv).getString())
									.map(s -> ActuatorUtils.validateHostKey(s, errors))
									.filter(Optional::isPresent)
									.map(Optional::get)
									.toArray(PublicKey[]::new)
					).orElse(new PublicKey[0]);

			if(hostKeys.length == 0) {
				valid = false;
			}

			Optional<Path> privateKey = TransportFactory.getOrNullIfEmpty(cfg, "keyfile").map(s -> Paths.get(URI.create(s)));
			if(privateKey.isEmpty()) {
				errors.add("No private key specified.");
				valid = false;
			}

			if(!valid) {
				return Optional.empty();
			}

			return Optional.of(new TransportFactory.Config(
					Optional.of(uri),
					hostKeys,
					privateKey,
					Optional.empty()
			));
		}
	};
}
