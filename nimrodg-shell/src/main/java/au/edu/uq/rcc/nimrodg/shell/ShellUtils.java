package au.edu.uq.rcc.nimrodg.shell;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.util.StringUtils;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Various utility functions.
 *
 * Some of these are thin wrappers around commons-exec to avoid exposing the dependency.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ShellUtils {

    public static String toAuthorizedKeyEntry(PublicKey pk) {
        return AuthorizedKeyEntry.toString(pk);
    }

    public static PublicKey parseAuthorizedKeyEntry(String s) throws IOException, GeneralSecurityException {
        return AuthorizedKeyEntry.parseAuthorizedKeyEntry(s)
                .resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
    }
    public static Optional<String> getUriUser(URI uri) {
        return getUriUser(Optional.ofNullable(uri));
    }

    public static Optional<String> getUriUser(Optional<URI> uri) {
        return uri.map(URI::getUserInfo).map(ui -> ui.split(":", 2)[0]);
    }

    public static RemoteShell.CommandResult doProcessOneshot(Process p, String[] args, byte[] input) throws IOException {
        ByteArrayInputStream stdin = new ByteArrayInputStream(input);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        PumpStreamHandler psh = new PumpStreamHandler(stdout, stderr, stdin);
        psh.setProcessOutputStream(p.getInputStream());
        psh.setProcessErrorStream(p.getErrorStream());
        psh.setProcessInputStream(p.getOutputStream());

        psh.start();

        while(p.isAlive()) {
            try {
                p.waitFor();
            } catch(InterruptedException e) {
                /* nop */
            }
        }

        psh.stop();

        return new RemoteShell.CommandResult(
                buildEscapedCommandLine(args),
                p.exitValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    public static RemoteShell.CommandResult doProcessOneshot(CommandLine cmd, byte[] input, Logger logger) throws IOException {
        String cmdline = buildEscapedCommandLine(cmd.toStrings());
        logger.trace("Executing command: {}", cmdline);

        ByteArrayInputStream stdin = new ByteArrayInputStream(input);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Executor exec = new DefaultExecutor();
        exec.setStreamHandler(new PumpStreamHandler(stdout, stderr, stdin));
        exec.setExitValues(null);

        int rv;
        try {
            rv = exec.execute(cmd);
        } catch(ExecuteException e) {
            Throwable t = e.getCause();
            if(t != null) {
                if(t instanceof IOException) {
                    throw (IOException)t;
                }
                throw new IOException(t);
            }
            rv = e.getExitValue();
        }

        return new RemoteShell.CommandResult(cmdline, rv, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    public static RemoteShell.CommandResult doProcessOneshot(String[] args, byte[] input, Logger logger) throws IOException {
        if(args.length == 0) {
            throw new IllegalArgumentException();
        }

        CommandLine cmd = new CommandLine(args[0]);
        for(int i = 1; i < args.length; ++i) {
            cmd.addArgument(args[i]);
        }

        return doProcessOneshot(cmd, input, logger);
    }

    public static RemoteShell.CommandResult doProcessOneshot(String[] args, Logger logger) throws IOException {
        return doProcessOneshot(args, new byte[0], logger);
    }

    public static String buildEscapedCommandLine(List<String> args) {
        return args.stream().map(ShellUtils::quoteArgument).collect(Collectors.joining(" "));
    }

    public static String buildEscapedCommandLine(String... args) {
        Objects.requireNonNull(args, "args");
        return Arrays.stream(args).map(ShellUtils::quoteArgument).collect(Collectors.joining(" "));
    }

    private static final Pattern ENV_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_+]*)=(.*)$");

    public static Map<String, String> readEnvironment(RemoteShell shell) throws IOException {
        Map<String, String> envs = new HashMap<>();
        SshdClient.CommandResult env = shell.runCommand("env");
        if(env.status != 0) {
            throw new IOException("Error retrieving environment");
        }

        String[] lines = env.stdout.split("[\\r\\n]+");

        for(String l : lines) {
            Matcher m = ENV_PATTERN.matcher(l);
            if(!m.matches()) {
                //throw new IOException("Error retrieving environment, malformed 'env' output");

                /*
                 * Tell me this isn't horrible. This was on Flashlite.
                 * G_BROKEN_FILENAMES=1
                 * BASH_FUNC_module()=() {  eval `/usr/bin/modulecmd bash $*`
                 * }
                 * _=/bin/env
                 */
                continue;
            }
            envs.put(m.group(1), m.group(2));
        }
        return envs;
    }

    @SuppressWarnings("OctalInteger")
    public static Set<PosixFilePermission> posixIntToPermissions(int perms) {
        Set<PosixFilePermission> s = new HashSet<>();

        if((perms & 0400) != 0) {
            s.add(PosixFilePermission.OWNER_READ);
        }

        if((perms & 0200) != 0) {
            s.add(PosixFilePermission.OWNER_WRITE);
        }

        if((perms & 0100) != 0) {
            s.add(PosixFilePermission.OWNER_EXECUTE);
        }

        if((perms & 040) != 0) {
            s.add(PosixFilePermission.GROUP_READ);
        }

        if((perms & 020) != 0) {
            s.add(PosixFilePermission.GROUP_WRITE);
        }

        if((perms & 010) != 0) {
            s.add(PosixFilePermission.GROUP_EXECUTE);
        }

        if((perms & 04) != 0) {
            s.add(PosixFilePermission.OTHERS_READ);
        }

        if((perms & 02) != 0) {
            s.add(PosixFilePermission.OTHERS_WRITE);
        }

        if((perms & 01) != 0) {
            s.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return s;
    }

    @SuppressWarnings("OctalInteger")
    public static int permissionsToInt(Collection<PosixFilePermission> perms) {
        int operms = 0;
        if(perms.contains(PosixFilePermission.OWNER_READ)) {
            operms |= 0400;
        }

        if(perms.contains(PosixFilePermission.OWNER_WRITE)) {
            operms |= 0200;
        }

        if(perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
            operms |= 0100;
        }

        if(perms.contains(PosixFilePermission.GROUP_READ)) {
            operms |= 040;
        }

        if(perms.contains(PosixFilePermission.GROUP_WRITE)) {
            operms |= 020;
        }

        if(perms.contains(PosixFilePermission.GROUP_EXECUTE)) {
            operms |= 010;
        }

        if(perms.contains(PosixFilePermission.OTHERS_READ)) {
            operms |= 04;
        }

        if(perms.contains(PosixFilePermission.OTHERS_WRITE)) {
            operms |= 02;
        }

        if(perms.contains(PosixFilePermission.OTHERS_EXECUTE)) {
            operms |= 01;
        }
        return operms;
    }

    public static String quoteArgument(final String argument) {
        return StringUtils.quoteArgument(argument);
    }

    public static String[] translateCommandline(String toProcess) {
        return CommandLine.parse(toProcess).toStrings();
    }

    /* Sheer and utter paranoia. */
    public static ByteChannel newByteChannelSafe(Path path, Set<PosixFilePermission> perms) throws IOException {
        Set<OpenOption> opts = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        FileAttribute[] atts;
        if(path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            atts = new FileAttribute[]{ PosixFilePermissions.asFileAttribute(perms) };
        } else {
            /* TODO: Convert perms to what they should be. */
            atts = new FileAttribute[0];
        }

        /*
         * Explicitly delete the file, as Files.newByteChannel() doesn't apply the attributes if it does.
         * Also, an adversary may already have an open file handle.
         *
         * There is a *slight* race here, but Java doesn't provide a way to do this atomically.
         * If the race does happen, and someone's created a file in the same place, this will throw.
         */
        Files.deleteIfExists(path);
        return Files.newByteChannel(path, opts, atts);
    }
}
