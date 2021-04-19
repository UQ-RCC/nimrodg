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
package au.edu.uq.rcc.nimrodg.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;


public class LocalShell implements RemoteShell {

	private static final Logger LOGGER = LoggerFactory.getLogger(LocalShell.class);

	@Override
	public CommandResult runCommand(String[] args, byte[] stdin) throws IOException {
		return ShellUtils.doProcessOneshot(args, stdin, LOGGER);
	}

	@Override
	public void upload(String destPath, byte[] bytes, Set<PosixFilePermission> perms, Instant timestamp) throws IOException {
		Path path = Paths.get(destPath);

		try(ByteChannel c = ShellUtils.newByteChannelSafe(path, perms)) {
			c.write(ByteBuffer.wrap(bytes));
		}

		Files.setLastModifiedTime(path, FileTime.from(timestamp));
	}

	@Override
	public Map<String, String> getEnvironment() {
		return Collections.unmodifiableMap(System.getenv());
	}

	@Override
	public void close() {
		/* nop */
	}
}
