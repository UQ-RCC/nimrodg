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
package au.edu.uq.rcc.nimrodg.shell;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A small interface around a remote system. Used so there can be multiple transports, i.e. Mina SSHD and a local ssh
 * exec.
 */
public interface RemoteShell extends Closeable {

	final class CommandResult {

		public final String commandLine;
		public final int status;
		public final String stdout;
		public final String stderr;

		public CommandResult(String commandLine, int status, String stdout, String stderr) {
			this.commandLine = Objects.requireNonNull(commandLine, "commandLine");
			this.status = status;
			this.stdout = Objects.requireNonNull(stdout, "stdout");
			this.stderr = Objects.requireNonNull(stderr, "stderr");
		}

		public CommandResult(String[] argv, int status, String stdout, String stderr) {
			this(ShellUtils.buildEscapedCommandLine(argv), status, stdout, stderr);
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			CommandResult that = (CommandResult)o;
			return status == that.status &&
					commandLine.equals(that.commandLine) &&
					stdout.equals(that.stdout) &&
					stderr.equals(that.stderr);
		}

		@Override
		public int hashCode() {
			return Objects.hash(commandLine, status, stdout, stderr);
		}

		@Override
		public String toString() {
			return "CommandResult{" +
					"commandLine='" + commandLine + '\'' +
					", status=" + status +
					", stdout='" + stdout + '\'' +
					", stderr='" + stderr + '\'' +
					'}';
		}
	}

	default CommandResult runCommand(String... args) throws IOException {
		return this.runCommand(args, new byte[0]);
	}

	CommandResult runCommand(String[] args, byte[] stdin) throws IOException;

	void upload(String destPath, byte[] bytes, Set<PosixFilePermission> perms, Instant timestamp) throws IOException;

	default void upload(String destPath, Path path, Set<PosixFilePermission> perms, Instant timestamp) throws IOException {
		upload(destPath, Files.readAllBytes(path), perms, timestamp);
	}

	default Map<String, String> getEnvironment() throws IOException {
		return ShellUtils.readEnvironment(this);
	}
}
