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
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalShell implements RemoteShell {

	private static final Logger LOGGER = LogManager.getLogger(LocalShell.class);

	public static final String TRANSPORT_NAME = "local";

	@Override
	public CommandResult runCommand(String... args) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
		pb.redirectError(ProcessBuilder.Redirect.PIPE);
		pb.redirectInput(ProcessBuilder.Redirect.PIPE);

		LOGGER.trace("Executing command: {}", ActuatorUtils.posixBuildEscapedCommandLine(args));

		Process p = pb.start();
		try {
			return ActuatorUtils.doProcessOneshot(p, args, new byte[0]);
		} catch(IOException e) {
			String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			LOGGER.error(err);
			throw e;
		} finally {
			p.destroyForcibly();
		}
	}

	@Override
	public void upload(String destPath, byte[] bytes, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		Path path = Paths.get(destPath);
		Files.write(path, bytes);
		Files.setPosixFilePermissions(path, new HashSet<>(perms));
		Files.setLastModifiedTime(path, FileTime.from(timestamp));
	}

	@Override
	public void close() {
		/* nop */
	}

	public static TransportFactory FACTORY = new TransportFactory() {
		@Override
		public RemoteShell create(TransportFactory.Config cfg) throws IOException {
			return new LocalShell();
		}

		@Override
		public Optional<TransportFactory.Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			if(!TRANSPORT_NAME.equals(cfg.getString("name"))) {
				errors.add("Invalid transport name, this is a bug.");
				return Optional.empty();
			}

			return Optional.of(new TransportFactory.Config(
					Optional.empty(),
					Optional.empty(),
					new PublicKey[0],
					Optional.empty(),
					Optional.empty(),
					Optional.empty()
			));
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) throws IOException {
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			return Json.createObjectBuilder().add("name", TRANSPORT_NAME).build();
		}
	};
}
