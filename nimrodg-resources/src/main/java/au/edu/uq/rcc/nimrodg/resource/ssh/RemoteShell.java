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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Collection;

/**
 * A small interface around a remote system. Used so there can be multiple transports, i.e. Mina SSHD and a local ssh
 * exec.
 */
public interface RemoteShell extends Closeable {

	public static final class CommandResult {

		public final String commandLine;
		public final int status;
		public final String stdout;
		public final String stderr;

		public CommandResult(String commandLine, int status, String stdout, String stderr) {
			this.commandLine = commandLine;
			this.status = status;
			this.stdout = stdout;
			this.stderr = stderr;
		}
	}

	CommandResult runCommand(String... args) throws IOException;

	void upload(String destPath, byte[] bytes, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException;

	default void upload(String destPath, Path path, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		upload(destPath, Files.readAllBytes(path), perms, timestamp);
	}
}
