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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

public interface TransportFactory {

	public static class Config {

		public final URI uri;
		public final Optional<String> user;
		public final PublicKey[] hostKeys;
		public final Optional<Path> privateKey;
		public final Optional<KeyPair> keyPair;
		public final Optional<Path> executablePath;

		public Config(URI uri, Optional<String> user, PublicKey[] hostKeys, Optional<Path> privateKey, Optional<KeyPair> keyPair, Optional<Path> executablePath) {
			this.uri = uri;
			this.user = user;
			this.hostKeys = Arrays.copyOf(hostKeys, hostKeys.length);
			this.privateKey = privateKey;
			this.keyPair = keyPair;
			this.executablePath = executablePath;
		}

	};

	RemoteShell create(Config cfg) throws IOException;

	/**
	 * Validate the transport's JSON configuration.
	 *
	 * This should only validate semantics. Structure validation should be put in the schema file.
	 *
	 * @param cfg The transport's JSON config.
	 * @param errors A list where error messages should be written.
	 * @return If the configuration is valid a {@link Config} object, otherwise and empty optional.
	 */
	Optional<Config> validateConfiguration(JsonObject cfg, List<String> errors);

	Config resolveConfiguration(Config cfg) throws IOException;

	JsonObject buildJsonConfiguration(Config cfg);

	// FIXME: This should really go in a helper class
	public static Optional<String> getOrNullIfEmpty(JsonObject j, String name) {
		return Optional.ofNullable(j.get(name))
				.filter(jj -> jj.getValueType() == JsonValue.ValueType.STRING)
				.map(jj -> ((JsonString)jj).getString())
				.filter(s -> !s.isEmpty());
	}

}
