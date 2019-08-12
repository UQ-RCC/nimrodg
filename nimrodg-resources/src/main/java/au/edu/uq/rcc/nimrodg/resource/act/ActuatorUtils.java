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
package au.edu.uq.rcc.nimrodg.resource.act;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ActuatorUtils {

	public static KeyPair readPEMKey(byte[] keyBytes) throws IOException {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

		Reader r = new InputStreamReader(new ByteArrayInputStream(keyBytes));
		PEMKeyPair p = (PEMKeyPair)new org.bouncycastle.openssl.PEMParser(r).readObject();

		return new JcaPEMKeyConverter().getKeyPair(p);
	}

	public static KeyPair readPEMKey(String key) throws IOException {
		return readPEMKey(key.getBytes(StandardCharsets.UTF_8));
	}

	public static KeyPair readPEMKey(Path path) throws IOException {
		return readPEMKey(Files.readAllBytes(path));
	}

	public static Certificate[] readX509Certificates(String path) throws IOException, CertificateException {
		if(path == null || path.isEmpty()) {
			return new Certificate[0];
		}

		return readX509Certificates(Paths.get(path));
	}

	public static Certificate[] readX509Certificates(Path path) throws IOException, CertificateException {
		try(InputStream is = Files.newInputStream(path)) {
			return readX509Certificates(is);
		}
	}

	public static Certificate[] readX509Certificates(InputStream is) throws IOException, CertificateException {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		List<Certificate> certs = new ArrayList<>();
		certs.addAll(cf.generateCertificates(is));
		return certs.toArray(new Certificate[certs.size()]);
	}

	public static void writeCertificatesToPEM(Path path, Certificate[] certs) throws IOException {
		try(OutputStream os = Files.newOutputStream(path)) {
			writeCertificatesToPEM(os, certs);
		}
	}

	public static byte[] writeCertificatesToPEM(Certificate[] certs) {
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			writeCertificatesToPEM(baos, certs);
			return baos.toByteArray();
		} catch(IOException e) {
			/* Will never happen. */
			throw new IllegalStateException(e);
		}
	}

	public static void writeCertificatesToPEM(OutputStream os, Certificate[] certs) throws IOException {
		try(JcaPEMWriter pemw = new JcaPEMWriter(new OutputStreamWriter(os, StandardCharsets.US_ASCII))) {
			for(Certificate c : certs) {
				pemw.writeObject(c);
			}
		}
	}

	public static String base64EncodeCertificates(Certificate[] certs) {
		byte[] certData;
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			writeCertificatesToPEM(baos, certs);
			baos.flush();
			certData = baos.toByteArray();
		} catch(IOException e) {
			/* Will never happen. */
			throw new IllegalStateException(e);
		}

		return Base64.getEncoder().encodeToString(certData);
	}

	@FunctionalInterface
	public interface JsonSchemaLoader extends Function<String, InputStream> {

	}

	public static boolean validateAgainstSchema(String schemaFile, JsonSchemaLoader loader, JsonStructure cfg, List<String> errors) {
		try(InputStream is = loader.apply(schemaFile)) {
			/* I actually don't care anymore. */
			SchemaLoader _loader = SchemaLoader.builder()
					.httpClient(loader::apply)
					.schemaJson(new JSONObject(new JSONTokener(is)))
					.build();

			Schema schema = _loader.load().build();
			schema.validate(new JSONObject(new JSONTokener(cfg.toString())));
		} catch(IOException e) {
			errors.add(e.getMessage());
			return false;
		} catch(ValidationException e) {
			errors.addAll(e.getAllMessages());
			return false;
		}
		return true;
	}

	public static boolean validateAgainstSchemaStandalone(JsonObject schema, JsonStructure json, List<String> errors) {
		Schema _schema = SchemaLoader.load(new JSONObject(schema.toString()), s -> {
			throw new UnsupportedOperationException();
		});

		try {
			_schema.validate(new JSONObject(json.toString()));
		} catch(ValidationException e) {
			errors.addAll(e.getAllMessages());
			return false;
		}
		return true;
	}

	public static String posixBuildEscapedCommandLine(List<String> args) {
		return posixBuildEscapedCommandLine(args.toArray(new String[args.size()]));
	}

	public static String posixBuildEscapedCommandLine(String... args) {
		StringBuilder sb = new StringBuilder();

		for(int i = 0; i < args.length; ++i) {
			sb.append(posixQuoteArgument(args[i]));
			if(i != args.length - 1) {
				sb.append(' ');
			}
		}

		return sb.toString();
	}

	public static String posixJoinPaths(String... args) {
		/* We can't rely on nio.Path here, separators might be wrong.  */
		//return String.join("/", args);

		String path = "";
		for(String c : args) {
			path = FilenameUtils.concat(path, c);
		}

		return FilenameUtils.separatorsToUnix(path);
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
	 *
	 * Ripped from Apache commons-exec
	 * https://github.com/apache/commons-exec/blob/trunk/src/main/java/org/apache/commons/exec/util/StringUtils.java
	 *
	 * NOTICE: Apache Commons Exec Copyright 2005-2016 The Apache Software Foundation
	 *
	 * This product includes software developed at The Apache Software Foundation (http://www.apache.org/).
	 */
	public static String posixQuoteArgument(final String argument) {

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

	public static ArrayList<String> posixBuildLaunchCommand(String agentPath, UUID uuid, String workRoot, NimrodURI uri, String routingKey, Optional<String> certPath, boolean b64cert, boolean keepCerts, boolean batch) {
		ArrayList<String> args = new ArrayList<>();
		args.add(agentPath);
		args.add("--uuid");
		args.add(uuid.toString());
		args.add("--amqp-uri");
		args.add(uri.uri.toASCIIString());
		args.add("--amqp-routing-key");
		args.add(routingKey);

		String scheme = uri.uri.getScheme().toLowerCase(Locale.ENGLISH);
		if(scheme.equals("amqps")) {
			if(uri.noVerifyPeer) {
				args.add("--no-verify-peer");
			}

			if(uri.noVerifyHost) {
				args.add("--no-verify-host");
			}

			if(certPath.isPresent()) {
				args.add("--caenc");
				args.add(b64cert ? "base64" : "plain");

				args.add("--cacert");
				args.add(certPath.get());

				if(keepCerts) {
					args.add("--no-ca-delete");
				}
			}
		} else if(!scheme.equals("amqp")) {
			throw new IllegalArgumentException("Invalid URI scheme");
		}

		args.add("--work-root");
		args.add(workRoot);

		if(batch) {
			args.add("--batch");

			args.add("--output");
			args.add("workroot");
		}
		return args;
	}

	@FunctionalInterface
	public interface ArgGenerator {

		void accept(StringBuilder sb, UUID[] uuids, String out, String err);
	}

	public static String posixBuildSubmissionScriptMulti(UUID[] uuids, String out, String err, String workRoot, NimrodURI uri, String routingKey, String agentPath, Optional<String> certPath, boolean b64certs, boolean keepCerts, ArgGenerator argProc) {
		StringBuilder script = new StringBuilder();
		script.append("#!/bin/sh\n");
		/* Apply the submission arguments */
		if(argProc != null) {
			argProc.accept(script, uuids, out, err);
		}
		script.append("\nPIDS=\"\"\ntrap 'kill -15 $PIDS; wait' INT HUP QUIT TERM\n\n");

		List<List<String>> preCommands = new ArrayList<>();
		List<List<String>> agentCommands = new ArrayList<>();
		List<List<String>> postCommands = new ArrayList<>();

		for(UUID uuid : uuids) {
			String workDir = String.format("%s/agent-%s", workRoot, uuid);
			String output = String.format("%s/output.txt", workDir);

			List<String> agentCmd = posixBuildLaunchCommand(agentPath, uuid, workRoot, uri, routingKey, certPath, b64certs, keepCerts, false);
			agentCmd.add(">");
			agentCmd.add(output);
			agentCmd.add("2>&1");
			agentCmd.add("&");

			preCommands.add(List.of("mkdir", "-p", workDir));
			agentCommands.add(agentCmd);
		}

		/* Build the shell commands */
		script.append("# Generated pre-agent commands\n");
		for(List<String> cmd : preCommands) {
			script.append(posixBuildEscapedCommandLine(cmd));
			script.append("\n");
		}
		script.append("\n");

		script.append("# Generated agent commands\n");
		for(List<String> cmd : agentCommands) {
			script.append(posixBuildEscapedCommandLine(cmd));
			script.append("\nPIDS=\"$PIDS $!\"\n\n");
		}
		script.append("\n\n");

		script.append("# Generated post-agent commands\n");
		for(List<String> cmd : postCommands) {
			script.append(posixBuildEscapedCommandLine(cmd));
			script.append("\n");
		}

		script.append("wait\n");
		return script.toString();
	}

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

	public static int posixPermissionsToInt(Collection<PosixFilePermission> perms) {
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

	public static URI validateUriString(String _uri, List<String> errors) {
		boolean valid = true;

		URI uri;
		try {
			uri = new URI(_uri);
		} catch(URISyntaxException ex) {
			errors.add("Invalid URI Syntax.");
			return null;
		}

		String scheme = uri.getScheme();
		if(scheme == null || !scheme.equalsIgnoreCase("ssh")) {
			errors.add("URI scheme must be ssh://");
			valid = false;
		}

		String host = uri.getHost();
		if(host == null) {
			errors.add("URI must have a host.");
			valid = false;
		}

		String userInfo = uri.getUserInfo();
		if(userInfo != null) {
			/* Allow "user", "user:", but not "user:pass". See RFC3986. */
			String[] ui = userInfo.split(":", 2);
			if(ui.length > 1 && !ui[1].isEmpty()) {
				errors.add("URI may not contain a password.");
				valid = false;
			}
		}

		return valid ? uri : null;
	}

	public static PublicKey loadHostKey(String key) throws IOException, GeneralSecurityException {
		AuthorizedKeyEntry hk = AuthorizedKeyEntry.parseAuthorizedKeyEntry(key);
		return hk.resolvePublicKey(PublicKeyEntryResolver.FAILING);
	}

	public static Optional<PublicKey> validateHostKey(String key, List<String> errors) {
		if(key.isEmpty()) {
			return Optional.empty();
		}

		try {
			return Optional.of(loadHostKey(key));
		} catch(IllegalArgumentException | IOException | GeneralSecurityException e) {
			errors.add("Invalid host key.");
			errors.addAll(Arrays.stream(e.getStackTrace()).map(ee -> ee.toString()).collect(Collectors.toList()));
		}

		return Optional.empty();
	}

	public static Optional<String> getUriUser(URI uri) {
		return getUriUser(Optional.ofNullable(uri));
	}

	public static Optional<String> getUriUser(Optional<URI> uri) {
		return uri.map(u -> u.getUserInfo()).map(ui -> ui.split(":", 1)[0]);
	}

	public static Optional<AgentInfo> lookupAgentByPlatform(AgentProvider ap, String plat, List<String> errors) {
		Optional<AgentInfo> op = Optional.ofNullable(plat).map(p -> ap.lookupAgentByPlatform(plat));
		if(!op.isPresent()) {
			errors.add(String.format("No agent exists with platform string '%s'.", plat));
		}
		return op;
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
		return new RemoteShell.CommandResult(ActuatorUtils.posixBuildEscapedCommandLine(args), p.exitValue(), output, error);
	}

	public static RemoteShell.CommandResult doProcessOneshot(Process p, String[] args) throws IOException {
		return doProcessOneshot(p, args, new byte[0]);
	}

	private static RemoteShell.CommandResult doProcessOneshot(String[] args, byte[] input, Logger logger) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(args);
		pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
		pb.redirectError(ProcessBuilder.Redirect.PIPE);
		pb.redirectInput(ProcessBuilder.Redirect.PIPE);

		logger.trace("Executing command: {}", ActuatorUtils.posixBuildEscapedCommandLine(args));

		Process p = pb.start();
		try {
			return ActuatorUtils.doProcessOneshot(p, args, input);
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

	/**
	 * Crack a command line.
	 *
	 * @param toProcess the command line to process.
	 * @return the command line broken into strings. An empty or null toProcess parameter results in a zero sized array.
	 *
	 * Taken from https://github.com/apache/ant/blob/master/src/main/org/apache/tools/ant/types/Commandline.java
	 * Revision 790e27474ff11b42f1d3f355fa8b0d34be10e321 Changed to throw IllegalArgumentException instead of
	 * BuildException
	 *
	 * NOTICE: Apache Ant Copyright 1999-2018 The Apache Software Foundation
	 *
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
