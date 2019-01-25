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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wrapper around OpenSSH. This entire class is a glorious hack.
 */
public class OpenSSHClient implements RemoteShell {

	private static final Logger LOGGER = LogManager.getLogger(OpenSSHClient.class);

	public static final String TRANSPORT_NAME = "openssh";

	public static final Optional<Path> DEFAULT_PRIVATE_KEY = Optional.empty();
	public static final String DEFAULT_EXECUTABLE = "ssh";

	public final URI uri;
	public final Optional<Path> privateKey;
	public final String executable;

	private final String[] sshArgs;

	public OpenSSHClient(URI uri) throws IOException {
		this(uri, DEFAULT_PRIVATE_KEY);
	}

	public OpenSSHClient(URI uri, Optional<Path> privateKey) throws IOException {
		this(uri, privateKey, DEFAULT_EXECUTABLE);
	}

	public OpenSSHClient(URI uri, Optional<Path> privateKey, String executable) throws IOException {
		this.uri = uri;
		this.privateKey = privateKey;

		if(executable == null) {
			executable = "ssh";
		}
		this.executable = executable;

		ArrayList<String> ssh = new ArrayList<>();
		ssh.add(executable);

		Optional<String> user = ActuatorUtils.getUriUser(uri);
		if(user.isPresent()) {
			ssh.add("-l");
			ssh.add(user.get());
		}

		if(privateKey.isPresent()) {
			ssh.add("-i");
			ssh.add(privateKey.get().toString());
		}

		int port = uri.getPort();
		if(port > 0) {
			ssh.add("-p");
			ssh.add(Integer.toString(port));
		}

		ssh.add("-q");

		ssh.add("-o");
		ssh.add("PasswordAuthentication=no");

		ssh.add("-o");
		ssh.add("StrictHostKeyChecking=no");

		//args.add("-tt");
		ssh.add(uri.getHost());

		ssh.add("--");

		sshArgs = ssh.stream().toArray(String[]::new);

		LOGGER.trace("OpenSSH: {}", ActuatorUtils.posixBuildEscapedCommandLine(ssh));
	}

	@Override
	public CommandResult runCommand(String... args) throws IOException {
		return runCommandInternal(args, new byte[0]);
	}

	public static void main(String[] args) throws IOException {
		try(OpenSSHClient c = new OpenSSHClient(URI.create("ssh://flashlite2"))) {
			//CommandResult cr = c.runCommand("echo", "asdf");

			c.upload(
					"/home/uqzvanim/testfile",
					"12345678\n".getBytes(StandardCharsets.UTF_8),
					EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
					Instant.now()
			);

			SystemInformation sysinfo = SystemInformation.getSystemInformation(c);
			int x = 0;
		}
	}

	@FunctionalInterface
	private interface ProcProc<T> {

		T run(Process p) throws IOException;
	}

	private <T> T runSsh(String[] args, ProcProc<T> proc) throws IOException {
		String[] aa = Stream.concat(
				Arrays.stream(sshArgs),
				Arrays.stream(args)
		).toArray(String[]::new);
		ProcessBuilder pb = new ProcessBuilder(aa);
		pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
		pb.redirectError(ProcessBuilder.Redirect.PIPE);
		pb.redirectInput(ProcessBuilder.Redirect.PIPE);

		LOGGER.trace("Executing command: {}", ActuatorUtils.posixBuildEscapedCommandLine(aa));

		Process p = pb.start();
		try {
			return proc.run(p);
		} catch(IOException e) {
			String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			LOGGER.error(err);
			throw e;
		} finally {
			p.destroyForcibly();
		}
	}

	private CommandResult runCommandInternal(String[] args, byte[] input) throws IOException {
		return this.runSsh(args, p -> {

			BufferedOutputStream stdin = new BufferedOutputStream(p.getOutputStream());
			BufferedInputStream stdout = new BufferedInputStream(p.getInputStream());
			BufferedInputStream stderr = new BufferedInputStream(p.getErrorStream());

			/* TODO: Do this properly in threads to avoid blocking. */
			if(input.length > 0) {
				stdin.write(input);
			}
			stdin.close();

			byte[] out = stdout.readAllBytes();
			byte[] err = stderr.readAllBytes();

			String output = new String(out, StandardCharsets.UTF_8);
			String error = new String(err, StandardCharsets.UTF_8).trim();

			while(p.isAlive()) {
				try {
					p.waitFor();
				} catch(InterruptedException e) {
					/* nop */
				}
			}
			return new CommandResult(ActuatorUtils.posixBuildEscapedCommandLine(args), p.exitValue(), output, error);
		});
	}

	private String readNextLine(InputStream is) throws IOException {
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(int c; (c = is.read()) != '\n';) {
				baos.write(c);
			}

			return baos.toString(StandardCharsets.UTF_8);
		}
	}

	@Override
	public void upload(String destPath, byte[] bytes, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		int operms = ActuatorUtils.posixPermissionsToInt(perms);

		this.runSsh(new String[]{
			"scp", "-q", "-p", "-t", destPath
		}, p -> {
			OutputStream stdin = p.getOutputStream();
			InputStream stdout = p.getInputStream();
			InputStream stderr = p.getErrorStream();

			long sec = timestamp.toEpochMilli() / 1000;
			String cmd = String.format("T%d 0 %d 0\n", sec, sec);
			stdin.write(cmd.getBytes(StandardCharsets.UTF_8));
			stdin.flush();

			if(stdout.read() != 0) {
				throw new IOException(readNextLine(stdout));
			}

			String last = destPath.substring(destPath.lastIndexOf('/') + 1);
			cmd = String.format("C%04o %d %s\n", operms, bytes.length, ActuatorUtils.posixQuoteArgument(last));
			stdin.write(cmd.getBytes(StandardCharsets.UTF_8));
			stdin.flush();

			if(stdout.read() != 0) {
				throw new IOException(readNextLine(stdout));
			}

			stdin.write(bytes);
			stdin.flush();

			if(stdout.read() != 0) {
				throw new IOException(readNextLine(stdout));
			}

			stdin.write(0);
			stdin.close();

			while(p.isAlive()) {
				try {
					p.waitFor();
				} catch(InterruptedException ex) {
					/* nop */
				}
			}

			int ret = p.exitValue();
			if(ret != 0) {
				throw new IOException(new String(stderr.readAllBytes(), StandardCharsets.UTF_8));
			}

			return null;
		});
	}

	public static TransportFactory FACTORY = new TransportFactory() {
		@Override
		public RemoteShell create(TransportFactory.Config cfg) throws IOException {
			return new OpenSSHClient(cfg.uri, cfg.privateKey, cfg.executablePath.map(p -> p.toString()).orElse(null));
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) throws IOException {
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			return Json.createObjectBuilder()
					.add("name", TRANSPORT_NAME)
					.add("uri", cfg.uri.toString())
					.add("keyfile", cfg.privateKey.map(p -> p.toString()).orElse(""))
					.add("executable", cfg.executablePath.map(p -> p.toString()).orElse(""))
					.build();
		}

		@Override
		public Optional<TransportFactory.Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			if(!TRANSPORT_NAME.equals(cfg.getString("name"))) {
				errors.add("Invalid transport name, this is a bug.");
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

			return Optional.of(new TransportFactory.Config(
					uri,
					ActuatorUtils.getUriUser(uri),
					new PublicKey[0],
					TransportFactory.getOrNullIfEmpty(cfg, "keyfile").map(s -> Paths.get(s)),
					Optional.empty(),
					TransportFactory.getOrNullIfEmpty(cfg, "executable").map(s -> Paths.get(s))
			));
		}
	};

	@Override
	public void close() throws IOException {
		/* nop */
	}
}
