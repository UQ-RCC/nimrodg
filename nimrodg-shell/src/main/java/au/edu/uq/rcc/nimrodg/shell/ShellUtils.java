package au.edu.uq.rcc.nimrodg.shell;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

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
        return uri.map(URI::getUserInfo).map(ui -> ui.split(":", 1)[0]);
    }

    public static RemoteShell.CommandResult doProcessOneshot(Process p, String[] args, byte[] input) throws IOException {
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
        return new RemoteShell.CommandResult(buildEscapedCommandLine(args), p.exitValue(), output, error);
    }

    public static RemoteShell.CommandResult doProcessOneshot(Process p, String[] args) throws IOException {
        return doProcessOneshot(p, args, new byte[0]);
    }

    private static RemoteShell.CommandResult doProcessOneshot(String[] args, byte[] input, Logger logger) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);

        logger.trace("Executing command: {}", buildEscapedCommandLine(args));

        Process p = pb.start();
        try {
            return doProcessOneshot(p, args, input);
        } catch(IOException e) {
            logger.error(new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            throw e;
        } finally {
            p.destroyForcibly();
        }
    }

    public static RemoteShell.CommandResult doProcessOneshot(String[] args, Logger logger) throws IOException {
        return doProcessOneshot(args, new byte[0], logger);
    }

    public static String buildEscapedCommandLine(List<String> args) {
        return buildEscapedCommandLine(args.toArray(new String[args.size()]));
    }

    public static String buildEscapedCommandLine(String... args) {
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < args.length; ++i) {
            sb.append(quoteArgument(args[i]));
            if(i != args.length - 1) {
                sb.append(' ');
            }
        }

        return sb.toString();
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

    /**
     * Put quotes around the given String if necessary.
     * <p>
     * If the argument doesn't include spaces or quotes, return it as is. If it contains double quotes, use single
     * quotes - else surround the argument by double quotes.
     * </p>
     *
     * @param argument the argument to be quoted
     * @return the quoted argument
     * @throws IllegalArgumentException If argument contains both types of quotes
     *                                  <p>
     *                                  Ripped from Apache commons-exec
     *                                  https://github.com/apache/commons-exec/blob/trunk/src/main/java/org/apache/commons/exec/util/StringUtils.java
     *                                  <p>
     *                                  NOTICE: Apache Commons Exec Copyright 2005-2016 The Apache Software Foundation
     *                                  <p>
     *                                  This product includes software developed at The Apache Software Foundation (http://www.apache.org/).
     */
    public static String quoteArgument(final String argument) {

        final String SINGLE_QUOTE = "\'";
        final String DOUBLE_QUOTE = "\"";

        String cleanedArgument = argument.trim();

        // strip the quotes from both ends
        while(cleanedArgument.startsWith(SINGLE_QUOTE) || cleanedArgument.startsWith(DOUBLE_QUOTE)) {
            cleanedArgument = cleanedArgument.substring(1);
        }

        while(cleanedArgument.endsWith(SINGLE_QUOTE) || cleanedArgument.endsWith(DOUBLE_QUOTE)) {
            cleanedArgument = cleanedArgument.substring(0, cleanedArgument.length() - 1);
        }

        final StringBuilder buf = new StringBuilder();
        if(cleanedArgument.contains(DOUBLE_QUOTE)) {
            if(cleanedArgument.contains(SINGLE_QUOTE)) {
                throw new IllegalArgumentException("Can't handle single and double quotes in same argument");
            }
            return buf.append(SINGLE_QUOTE).append(cleanedArgument).append(SINGLE_QUOTE).toString();
        } else if(cleanedArgument.contains(SINGLE_QUOTE) || cleanedArgument.contains(" ")) {
            return buf.append(DOUBLE_QUOTE).append(cleanedArgument).append(DOUBLE_QUOTE).toString();
        } else {
            return cleanedArgument;
        }
    }


    /**
     * Crack a command line.
     *
     * @param toProcess the command line to process.
     * @return the command line broken into strings. An empty or null toProcess parameter results in a zero sized array.
     * <p>
     * Taken from https://github.com/apache/ant/blob/master/src/main/org/apache/tools/ant/types/Commandline.java
     * Revision 790e27474ff11b42f1d3f355fa8b0d34be10e321 Changed to throw IllegalArgumentException instead of
     * BuildException
     * <p>
     * NOTICE: Apache Ant Copyright 1999-2018 The Apache Software Foundation
     * <p>
     * This product includes software developed at The Apache Software Foundation (http://www.apache.org/).
     */
    public static String[] translateCommandline(String toProcess) {
        if(toProcess == null || toProcess.isEmpty()) {
            //no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while(tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch(state) {
                case inQuote:
                    if("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if(" ".equals(nextTok)) {
                        if(lastTokenHasBeenQuoted || current.length() > 0) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if(lastTokenHasBeenQuoted || current.length() > 0) {
            result.add(current.toString());
        }
        if(state == inQuote || state == inDoubleQuote) {
            throw new IllegalArgumentException("unbalanced quotes in " + toProcess);
        }
        return result.toArray(new String[result.size()]);
    }
}
