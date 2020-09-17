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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper around OpenSSH. This entire class is a glorious hack.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OpenSSHClient implements RemoteShell {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSSHClient.class);

    private final URI uri;
    private final Optional<Path> privateKey;
    private final Path executable;
    private final Path workDir;
    private final String uniqueStub;

    private final String[] sshArgs;
    private final String[] closeArgs;

    private int commandCount;

    public OpenSSHClient(URI uri, Path workDir, Optional<Path> privateKey, Optional<Path> executable, Map<String, String> opts) throws IOException {
        this.uri = uri;
        this.executable = executable.orElse(Paths.get("ssh"));
        this.workDir = workDir;

        long uniqueHash = Objects.hash(uri, Thread.currentThread().getId());
        this.uniqueStub = String.format("openssh-%d-", uniqueHash & 0xFFFFFFFFL);
        Path socketPath = workDir.resolve(uniqueStub + "control");

        if(opts.keySet().stream().anyMatch(k -> !k.matches("^[a-zA-Z0-9]+$"))) {
            throw new IllegalArgumentException("invalid custom option key");
        }

        if(opts.values().stream().anyMatch(v -> !v.matches("^[a-zA-Z0-9.-_@/]+$"))) {
            throw new IllegalArgumentException("invalid custom option value");
        }

        /* Option order always takes precedence, so use ours first. */
        List<String> commonArgs = Stream.concat(Stream.of(
                "-oPasswordAuthentication=no",
                "-oKbdInteractiveAuthentication=no",
                "-oChallengeResponseAuthentication=no",
                "-oBatchMode=yes",
                "-oControlMaster=auto",
                "-oControlPersist=yes",
                String.format("-oControlPath=%s", socketPath)
        ), opts.entrySet().stream().map(e -> String.format("-o%s=%s", e.getKey(), e.getValue())))
                .collect(Collectors.toList());

        ArrayList<String> ssh = new ArrayList<>();
        ssh.add(this.executable.toString());

        ShellUtils.getUriUser(uri).ifPresent(u -> {
            ssh.add("-l");
            ssh.add(u);
        });

        if(privateKey.isPresent()) {
            /* The key may be on another filesystem, so make a "local" copy of it. */
            this.privateKey = Optional.of(workDir.resolve(uniqueStub + "key"));

            try(ByteChannel c = ShellUtils.newByteChannelSafe(this.privateKey.get(), EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))) {
                c.write(ByteBuffer.wrap(Files.readAllBytes(privateKey.get())));
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
        sshArgs = ssh.toArray(new String[0]);

        {
            ssh.clear();
            ssh.add(this.executable.toString());
            ssh.addAll(commonArgs);
            ssh.add("-O");
            ssh.add("exit");
            ssh.add(uri.getHost());
            closeArgs = ssh.toArray(new String[0]);
        }

        commandCount = 0;
    }

    @Override
    public CommandResult runCommand(String[] args, byte[] stdin) throws IOException {
        return this.runSsh(args, p -> ShellUtils.doProcessOneshot(p, args, stdin));
    }

    @FunctionalInterface
    private interface ProcProc {
        CommandResult run(Process p) throws IOException;
    }

    private CommandResult runSsh(String[] args, ProcProc proc) throws IOException {
        Path logPath = workDir.resolve(String.format("%slog%02d.txt", uniqueStub, commandCount++));

        ArrayList<String> aa = new ArrayList<>(sshArgs.length + 3 + args.length);
        aa.addAll(Arrays.asList(sshArgs));
        aa.add("-E");
        aa.add(logPath.toString());
        aa.add(uri.getHost());
        aa.add("--");
        aa.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(aa);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        String escCmd = ShellUtils.buildEscapedCommandLine(aa);

        LOGGER.trace("Executing command: {}", escCmd);
        Process p = pb.start();

        boolean forceLog = false;
        CommandResult cr;
        try {
            cr = proc.run(p);
        } catch(IOException e) {
            forceLog = true;
            throw e;
        } finally {
            p.destroyForcibly();
            handleLog(logPath, forceLog);
        }

        if(cr.status == 255) {
            LOGGER.error("OpenSSH execution failed: {}", escCmd);
            throw new IOException("OpenSSH execution failed");
        }
        return cr;
    }

    /* Try our damned hardest to dump the log file for tracing purposes. */
    private void handleLog(Path logPath, boolean force) {
        if(!LOGGER.isTraceEnabled() && !force) {
            return;
        }

        LOGGER.trace("Attempting to dump OpenSSH log file at {}", logPath);

        byte[] log;
        try {
            log = Files.readAllBytes(logPath);
        } catch(IOException e) {
            LOGGER.trace("Unable to read log file", e);
            return;
        }

        String slog;
        try {
            slog = new String(log, StandardCharsets.UTF_8);
        } catch(IllegalArgumentException e) {
            LOGGER.trace("Unable to parse log file as UTF-8, dumping as base64", e);
            slog = Base64.getEncoder().encodeToString(log);
        }

        LOGGER.trace(slog);

        try {
            Files.delete(logPath);
        } catch(IOException e) {
            LOGGER.trace("Unable to remove log file", e);
        }
    }

    private String readNextLine(InputStream is) throws IOException {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for(int c; (c = is.read()) != '\n'; ) {
                baos.write(c);
            }

            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    @Override
    public void upload(String destPath, byte[] bytes, Set<PosixFilePermission> perms, Instant timestamp) throws IOException {
        int operms = ShellUtils.permissionsToInt(perms);

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
            cmd = String.format("C%04o %d %s\n", operms, bytes.length, ShellUtils.quoteArgument(last));
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
            if(ret == 255) {
                /* There's special handling for this in runSsh(). */
                return new CommandResult(
                        "",
                        ret,
                        new String(stdout.readAllBytes(), StandardCharsets.UTF_8),
                        new String(stderr.readAllBytes(), StandardCharsets.UTF_8)
                );
            } else if(ret != 0) {
                throw new IOException(new String(stderr.readAllBytes(), StandardCharsets.UTF_8));
            }

            return new CommandResult("", 0, "", "");
        });
    }

    public URI getUri() {
        return uri;
    }

    public Path getExecutable() {
        return executable;
    }

    @Override
    public void close() throws IOException {
        /* This is safe as we've copied it. */
        if(privateKey.isPresent()) {
            try {
                Files.deleteIfExists(this.privateKey.get());
            } catch(IOException e) {
                LOGGER.warn("Unable to delete private key", e);
            }
        }
        ShellUtils.doProcessOneshot(closeArgs, LOGGER);
    }
}
