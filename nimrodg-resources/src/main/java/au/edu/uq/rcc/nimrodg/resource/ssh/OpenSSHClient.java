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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
	public static final Optional<Path> DEFAULT_EXECUTABLE = Optional.empty();

	public final URI uri;
	public final Optional<Path> privateKey;
	public final Path executable;

	private final String[] sshArgs;
	private final String[] closeArgs;

	public OpenSSHClient(URI uri, Path workDir) throws IOException {
		this(uri, workDir, DEFAULT_PRIVATE_KEY, DEFAULT_EXECUTABLE, Map.of());
	}

	public OpenSSHClient(URI uri, Path workDir, Optional<Path> privateKey, Optional<Path> executable, Map<String, String> opts) throws IOException {
		this.uri = uri;
		this.executable = executable.orElse(Paths.get("ssh"));

		Path socketPath = workDir.resolve(String.format("openssh-%d-control", (long)uri.hashCode() & 0xFFFFFFFFL));

		if(opts.keySet().stream().anyMatch(k -> !k.matches("^[a-zA-Z0-9]+$"))) {
			throw new IllegalArgumentException("invalid custom option key");
		}

		if(opts.values().stream().anyMatch(v -> !v.matches("^[a-zA-Z0-9.-_@/]+$"))) {
			throw new IllegalArgumentException("invalid custom option value");
		}

		/* Option order always takes precedence, so use ours first. */
		List<String> commonArgs = Stream.concat(Stream.of(
				"-q",
				"-oPasswordAuthentication=no",
				"-oBatchMode=yes",
				"-oControlMaster=auto",
				"-oControlPersist=yes",
				String.format("-oControlPath=%s", socketPath)
		), opts.entrySet().stream().map(e -> String.format("-o%s=%s", e.getKey(), e.getValue())))
				.collect(Collectors.toList());

		ArrayList<String> ssh = new ArrayList<>();
		ssh.add(this.executable.toString());

		ActuatorUtils.getUriUser(uri).ifPresent(u -> {
			ssh.add("-l");
			ssh.add(u);
		});

		if(privateKey.isPresent()) {
			/* The key may be on another filesystem, so make a "local" copy of it. */
			this.privateKey = Optional.of(workDir.resolve(String.format("openssh-%d-key", (long)uri.hashCode() & 0xFFFFFFFFL)));
			Files.copy(privateKey.get(), this.privateKey.get(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			try {
				Files.setPosixFilePermissions(privateKey.get(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			} catch(UnsupportedOperationException e) {
				LOGGER.catching(e);
			}
			ssh.add("-i");
			ssh.add(this.privateKey.get().toString());
		} else {
			this.privateKey = Optional.empty();
		}

		int port = uri.getPort();
		if(port > 0) {
			ssh.add("-p");
			ssh.add(Integer.toString(port));
		}

		ssh.addAll(commonArgs);
		//args.add("-tt");
		ssh.add(uri.getHost());
		ssh.add("--");
		sshArgs = ssh.stream().toArray(String[]::new);

		{
			ssh.clear();
			ssh.add(this.executable.toString());
			ssh.addAll(commonArgs);
			ssh.add("-O");
			ssh.add("exit");
			ssh.add(uri.getHost());
			closeArgs = ssh.stream().toArray(String[]::new);
		}

		LOGGER.trace("OpenSSH: {}", ActuatorUtils.posixBuildEscapedCommandLine(sshArgs));
	}

	@Override
	public CommandResult runCommand(String... args) throws IOException {
		return runCommandInternal(args, new byte[0]);
	}

	public static void main(String[] args) throws IOException {
		try(OpenSSHClient c = new OpenSSHClient(URI.create("ssh://flashlite2"), Paths.get("/tmp"))) {
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
		return this.runSsh(args, p -> ActuatorUtils.doProcessOneshot(p, args, input));
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
		public RemoteShell create(TransportFactory.Config cfg, Path workDir) throws IOException {
			if(!cfg.uri.isPresent()) {
				throw new IOException("No URI provided.");
			}

			return new OpenSSHClient(cfg.uri.get(), workDir, cfg.privateKey, cfg.executablePath, Map.of());
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) throws IOException {
			if(!cfg.uri.isPresent()) {
				throw new IOException("No URI provided.");
			}
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			return Json.createObjectBuilder()
					.add("name", TRANSPORT_NAME)
					.add("uri", cfg.uri.map(u -> u.toString()).orElse(""))
					.add("keyfile", cfg.privateKey.map(p -> p.toUri().toString()).orElse(""))
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
					Optional.of(uri),
					ActuatorUtils.getUriUser(uri),
					new PublicKey[0],
					TransportFactory.getOrNullIfEmpty(cfg, "keyfile").map(s -> Paths.get(URI.create(s))),
					TransportFactory.getOrNullIfEmpty(cfg, "executable").map(s -> Paths.get(s))
			));
		}
	};

	@Override
	public void close() throws IOException {
		/* This is safe as we've copied it. */
		if(privateKey.isPresent()) {
			try {
				Files.deleteIfExists(this.privateKey.get());
			} catch(IOException e) {
				LOGGER.catching(e);
			}
		}
		ActuatorUtils.doProcessOneshot(closeArgs, LOGGER);
	}
}
