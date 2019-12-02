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

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;

import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import org.apache.commons.io.FilenameUtils;
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

	public static String posixJoinPaths(String... args) {
		/* We can't rely on nio.Path here, separators might be wrong.  */
		//return String.join("/", args);

		String path = "";
		for(String c : args) {
			path = FilenameUtils.concat(path, c);
		}

		return FilenameUtils.separatorsToUnix(path);
	}

	@Deprecated
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

	public static JsonObjectBuilder buildBaseAgentConfig(NimrodURI uri, String routingKey, Optional<String> certPath, boolean b64cert, boolean keepCerts, boolean batch, Map<String, String> environment) {
		JsonObjectBuilder cfg = Json.createObjectBuilder()
				.add("amqp", Json.createObjectBuilder()
						.add("uri", uri.uri.toString())
						.add("routing_key", routingKey)
				).add("no_verify_peer", uri.noVerifyPeer)
				.add("no_verify_host", uri.noVerifyHost);


		certPath.ifPresent(s -> cfg.add("ca", Json.createObjectBuilder()
				.add("cert", s)
				.add("encoding", b64cert ? "base64" : "plain")
				.add("no_delete", keepCerts)));

		String scheme = uri.uri.getScheme().toLowerCase(Locale.ENGLISH);
		if(!"amqps".equals(scheme) && !"amqp".equals(scheme)) {
			throw new IllegalArgumentException("Invalid URI scheme");
		}

		cfg.add("batch", batch);
		if(batch) {
			cfg.add("output", "workroot");
		}

		JsonObjectBuilder envs = Json.createObjectBuilder();
		environment.forEach(envs::add);
		cfg.add("environment", envs);
		return cfg;
	}

	public static Map<String, String> resolveEnvironment(List<String> keys) {
		return keys.stream()
				.map(k -> Map.entry(k, System.getenv(k)))
				.filter(e -> e.getValue() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
			script.append(ShellUtils.buildEscapedCommandLine(cmd));
			script.append("\n");
		}
		script.append("\n");

		script.append("# Generated agent commands\n");
		for(List<String> cmd : agentCommands) {
			script.append(ShellUtils.buildEscapedCommandLine(cmd));
			script.append("\nPIDS=\"$PIDS $!\"\n\n");
		}
		script.append("\n\n");

		script.append("# Generated post-agent commands\n");
		for(List<String> cmd : postCommands) {
			script.append(ShellUtils.buildEscapedCommandLine(cmd));
			script.append("\n");
		}

		script.append("wait\n");
		return script.toString();
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

	public static Optional<PublicKey> validateHostKey(String key, List<String> errors) {
		if(key.isEmpty()) {
			return Optional.empty();
		}

		try {
			return Optional.of(ShellUtils.parseAuthorizedKeyEntry(key));
		} catch(IllegalArgumentException | IOException | GeneralSecurityException e) {
			errors.add("Invalid host key.");
			errors.addAll(Arrays.stream(e.getStackTrace()).map(ee -> ee.toString()).collect(Collectors.toList()));
		}

		return Optional.empty();
	}

	public static Optional<AgentInfo> lookupAgentByPlatform(AgentProvider ap, String plat, List<String> errors) {
		Optional<AgentInfo> op = Optional.ofNullable(plat).map(p -> ap.lookupAgentByPlatform(plat));
		if(!op.isPresent()) {
			errors.add(String.format("No agent exists with platform string '%s'.", plat));
		}
		return op;
	}

	public static Actuator.LaunchResult[] makeFailedLaunch(UUID[] uuid, Throwable t) {
		Actuator.LaunchResult[] lrs = new Actuator.LaunchResult[uuid.length];
		Arrays.fill(lrs, new Actuator.LaunchResult(null, t));
		return lrs;
	}
}
