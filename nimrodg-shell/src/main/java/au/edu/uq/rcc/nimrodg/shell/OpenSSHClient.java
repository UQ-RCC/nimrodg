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
import java.net.URISyntaxException;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper around OpenSSH. This entire class is a glorious hack.
 */
public class OpenSSHClient implements RemoteShell {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSSHClient.class);

    private final URI uri;
    private final Optional<Path> privateKey;
    private final Path executable;

    private final String[] sshArgs;
    private final String[] closeArgs;

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
            this.privateKey = Optional.of(workDir.resolve(String.format("openssh-%d-key", (long)uri.hashCode() & 0xFFFFFFFFL)));

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

        LOGGER.trace("OpenSSH: {}", ShellUtils.buildEscapedCommandLine(sshArgs));
    }

//        -L port:host:hostport
//        -L port:remote_socket
//        -L [bind_address:]port:host:hostport
//        -L [bind_address:]port:remote_socket
//        -L local_socket:host:hostport
//        -L local_socket:remote_socket
//    -R [bind_address:]port:host:hostport
//    -R [bind_address:]port:local_socket
//    -R remote_socket:host:hostport
//    -R remote_socket:local_socket
//    -R [bind_address:]port
//  -D [bind_address:]port

    private static void validateHostAndPort(String host, Optional<Integer> port) {
        port.ifPresent(p -> {
            if(p < 1 || p > 65535) {
                throw new IllegalArgumentException("invalid port number");
            }
        });

        /* Abuse URI to validate things. */
        URI uri;
        try {
            uri = new URI(null, null, host, port.orElse(-1), null, null, null);
        } catch(URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void forwardLocal(int port, String host, int hostPort) {
        /* Abuse URI to validate things. */
        URI uri;
        try {
            uri = new URI(null, null, host, hostPort, null, null, null);
        } catch(URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        String s = String.format("-L%d:%s:%d", port, uri.getHost(), uri.getPort());

        System.err.println(s);
    }

    public void forwardLocal(String bindAddress, int port, String host, int hostPort) {
        String.format("-L%s:%d:%s:%d", bindAddress, port, host, hostPort);
    }

    public void forwardLocal(int port, String remoteSocket) {
        String.format("-L%d:%s", port, remoteSocket);
    }

    public void forwardLocal(String bindAddress, int port, String remoteSocket) {
        String.format("-L%s:%d:%s", bindAddress, port, remoteSocket);
    }

    public void forwardLocal(String localSocket, String host, int hostPort) {
        String.format("-L%s:%s:%d", localSocket, host, hostPort);
    }

    public void forwardLocal(String localSocket, String remoteSocket) {
        String.format("-L%s:%s", localSocket, remoteSocket);
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
        String[] aa = Stream.concat(
                Arrays.stream(sshArgs),
                Arrays.stream(args)
        ).toArray(String[]::new);
        ProcessBuilder pb = new ProcessBuilder(aa);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        String escCmd = ShellUtils.buildEscapedCommandLine(aa);
        LOGGER.trace("Executing command: {}", escCmd);
        Process p = pb.start();

        CommandResult cr;
        try {
            cr = proc.run(p);
        } catch(IOException e) {
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            LOGGER.error(err);
            throw e;
        } finally {
            p.destroyForcibly();
        }

        if(cr.status == 255) {
            LOGGER.error("OpenSSH execution failed: {}", escCmd);
            LOGGER.error(cr.stderr);
            throw new IOException("OpenSSH execution failed");
        }
        return cr;
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
            if(ret != 0) {
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

    public static void main(String[] args) throws IOException {
        try(OpenSSHClient c = new OpenSSHClient(URI.create("ssh://0.0.0.0"), Paths.get("/tmp"), Optional.empty(), Optional.empty(), Map.of())) {

            c.forwardLocal(8080, "192.168.0.1", 443);
            c.forwardLocal(0, "192.168.0.1", 443);
            c.forwardLocal(8080, "192.168.0.1", 0);
            //c.forwardLocal(8080, "host:name", 443);
            //c.forwardLocal(8080, "[]", 443);
            c.forwardLocal(8080, "[::]", 443);
            //c.forwardLocal(8080, "host name", 443);

            c.forwardLocal(8080, "[::]", -1);
            //CommandResult cr = c.runCommand("echo", "asdf");

//            c.upload(
//                    "/home/uqzvanim/testfile",
//                    "12345678\n".getBytes(StandardCharsets.UTF_8),
//                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
//                    Instant.now()
//            );

//            CommandResult cr = c.runCommand("uname", "-a");
//            int x = 0;
        }
    }

}
