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
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClientCreator;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.scp.ScpTimestamp;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.util.net.SshdSocketAddress;

public class SSHClient implements RemoteShell {

	private static final Logger LOGGER = LogManager.getLogger(SSHClient.class);

	public static final String TRANSPORT_NAME = "sshd";

	public final URI uri;
	public final PublicKey[] hostKeys;
	public final KeyPair keyPair;
	public final SshClient client;
	public final ClientSession session;
	public final ScpClient scp;
	private final ArrayList<PortForwardingTracker> ptrackers;

	private boolean closed;

	public SSHClient(URI uri, PublicKey hostKey, KeyPair keyPair) throws IOException, IllegalArgumentException {
		this(uri, new PublicKey[]{hostKey}, keyPair);
	}

	public SSHClient(URI uri, PublicKey[] hostKeys, KeyPair keyPair) throws IOException, IllegalArgumentException {
		this.uri = uri;
		this.hostKeys = Arrays.copyOf(hostKeys, hostKeys.length);
		this.keyPair = keyPair;

		String scheme = uri.getScheme();
		if(!scheme.equals("ssh")) {
			throw new IllegalArgumentException("Invalid URI scheme");
		}

		String host = uri.getHost();
		if(host == null) {
			throw new IllegalArgumentException("No hostname sepecified");
		}

		int port = uri.getPort();
		if(port < 0) {
			port = 22;
		}

		String userInfo = uri.getUserInfo();
		if(userInfo == null) {
			throw new IllegalArgumentException("No username specified");
		}

		String[] userPass = userInfo.split(":", 1);
		assert userPass.length <= 2;

		Set<PublicKey> pks = Arrays.stream(hostKeys).collect(Collectors.toSet());

		client = ClientBuilder.builder()
				.serverKeyVerifier((ClientSession arg0, SocketAddress arg1, PublicKey key) -> pks.contains(key))
				.build();
		client.getProperties().put(ClientFactoryManager.HEARTBEAT_INTERVAL, TimeUnit.SECONDS.toMillis(2));
		client.start();

		ConnectFuture cf = client.connect(userPass[0], host, port);
		if(!cf.await()) {
			Throwable t = cf.getException();
			if((t instanceof IOException)) {
				throw (IOException)t;
			} else {
				throw new IOException(cf.getException());
			}
		}

		session = cf.getSession();
		session.addPublicKeyIdentity(keyPair);

		AuthFuture af = session.auth();
		/* Will throw IOException on failure */
		af.await();

		if(af.isFailure()) {
			try(SshClient c = client) {
				Throwable t = af.getException();
				if(t instanceof IOException) {
					throw (IOException)t;
				} else {
					throw new IOException(t);
				}
			}
		}

		scp = ScpClientCreator.instance().createScpClient(session);
		ptrackers = new ArrayList<>();
		closed = false;
	}

	@Override
	public void close() throws IOException {
		if(closed) {
			return;
		}

		try(SshClient c = client) {
			session.close();
		} finally {
			closed = true;
		}
	}

	public void createTunnel(String srcHost, int srcPort, String dstHost, int dstPort) throws IOException {
		//this.session.createRemotePortForwardingTracker(SshdSocketAddress.LOCALHOST_ADDRESS, SshdSocketAddress.LOCALHOST_ADDRESS)
		SshdSocketAddress src = new SshdSocketAddress(srcHost, srcPort);
		SshdSocketAddress dst = new SshdSocketAddress(dstHost, dstPort);
		ptrackers.add(session.createRemotePortForwardingTracker(dst, src));
	}

	@Override
	public CommandResult runCommand(String... args) throws IOException {
		return runCommand(session, args);
	}

	@Override
	public void upload(String destPath, byte[] bytes, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		long ms = timestamp.getEpochSecond();
		scp.upload(bytes, destPath, perms, new ScpTimestamp(ms, ms));

		/* There's an issue with Mina, where if the timestamp is nonnull, it sets the "preserve attributes" option, so
		 * our permissions are ignored. */
		int operms = ActuatorUtils.posixPermissionsToInt(perms);
		runCommand("chmod", Integer.toString(operms, 8), destPath);
	}

	public static CommandResult runCommand(ClientSession ses, String... args) throws IOException {
		ByteArrayOutputStream baosout = new ByteArrayOutputStream();
		ByteArrayOutputStream baoserr = new ByteArrayOutputStream();
		int ret;
		String cmdline = ActuatorUtils.posixBuildEscapedCommandLine(args);

		LOGGER.trace("Executing command: {}", cmdline);

		try(ChannelExec ch = ses.createExecChannel(cmdline)) {
			ch.setEnv("LANG", "POSIX");
			ch.setEnv("LC_ALL", "POSIX");
			ch.setOut(baosout);
			ch.setErr(baoserr);
			OpenFuture of = ch.open();
			if(!of.await()) {
				throw new IOException(of.getException());
			}
			ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 0);
			ret = ch.getExitStatus();
		}

		return new CommandResult(
				cmdline,
				ret,
				new String(baosout.toByteArray(), StandardCharsets.UTF_8),
				new String(baoserr.toByteArray(), StandardCharsets.UTF_8)
		);
	}

	public static PublicKey[] resolveHostKeys(String user, String host, int port) throws IOException {
		ClientBuilder cb = ClientBuilder.builder();

		List<PublicKey> pubKeys = new ArrayList<>();
		cb.serverKeyVerifier((ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) -> {
			pubKeys.add(serverKey);
			return true;
		});

		try(SshClient ssh = cb.build()) {
			ssh.start();

			for(BuiltinSignatures sig : BuiltinSignatures.VALUES) {
				ssh.setSignatureFactories(List.of(sig));
				ConnectFuture cf = ssh.connect(user, host, port < 0 ? 22 : port);
				if(!cf.await()) {
					Throwable t = cf.getException();
					if(t instanceof IOException) {
						throw (IOException)t;
					} else {
						throw new IOException(t);
					}
				}

				try(ClientSession ses = cf.getSession()) {
					AuthFuture af = ses.auth();
					af.await();
				}
			}
		}

		return pubKeys.stream().toArray(PublicKey[]::new);
	}

	public static TransportFactory FACTORY = new TransportFactory() {
		@Override
		public RemoteShell create(TransportFactory.Config cfg) throws IOException {
			if(cfg.hostKeys.length == 0) {
				throw new IOException("No host keys provided");
			}

			KeyPair kp;
			if(!cfg.keyPair.isPresent()) {
				if(!cfg.privateKey.isPresent()) {
					throw new IOException("No private key provided");
				}

				kp = ActuatorUtils.readPEMKey(cfg.privateKey.get());
			} else {
				kp = cfg.keyPair.get();
			}

			return new SSHClient(cfg.uri, cfg.hostKeys, kp);
		}

		@Override
		public TransportFactory.Config resolveConfiguration(TransportFactory.Config cfg) throws IOException {
			if(cfg.hostKeys.length > 0) {
				return cfg;
			}

			int port = cfg.uri.getPort();
			if(port <= 0) {
				port = 22;
			}

			PublicKey[] hostKeys = resolveHostKeys(cfg.user.orElse(""), cfg.uri.getHost(), port);
			if(hostKeys.length == 0) {
				throw new IOException("No host keys resolved");
			}

			return new TransportFactory.Config(
					cfg.uri,
					cfg.user,
					hostKeys,
					cfg.privateKey,
					cfg.keyPair,
					Optional.empty(),
					cfg.tunnels
			);
		}

		@Override
		public JsonObject buildJsonConfiguration(TransportFactory.Config cfg) {
			JsonArrayBuilder ja = Json.createArrayBuilder();
			for(PublicKey pk : cfg.hostKeys) {
				ja.add(AuthorizedKeyEntry.toString(pk));
			}

			return Json.createObjectBuilder()
					.add("name", TRANSPORT_NAME)
					.add("uri", cfg.uri.toString())
					.add("keyfile", cfg.privateKey.map(p -> p.toString()).orElse(""))
					.add("hostkeys", ja)
					.add("tunnels", TransportFactory.writeTunnels(cfg.tunnels))
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

			Optional<String> user = ActuatorUtils.getUriUser(uri);
			if(!user.isPresent()) {
				errors.add("No user specified.");
				valid = false;
			}

			/* Not going to lie, I got some perverse pleasure from doing this. */
			PublicKey[] hostKeys = Optional.ofNullable(cfg.get("hostkeys"))
					.filter(v -> JsonValue.ValueType.ARRAY.equals(v.getValueType()))
					.map(v -> ((JsonArray)v).stream()
							.filter(jv -> JsonValue.ValueType.STRING.equals(jv.getValueType()))
							.map(jv -> ((JsonString)jv).getString())
							.map(s -> ActuatorUtils.validateHostKey(s, errors))
							.filter(s -> s.isPresent())
							.map(s -> s.get())
							.toArray(PublicKey[]::new)
					).orElse(new PublicKey[0]);

			if(hostKeys.length == 0) {
				valid = false;
			}

			Optional<Path> privateKey = TransportFactory.getOrNullIfEmpty(cfg, "keyfile").map(s -> Paths.get(s));
			if(!privateKey.isPresent()) {
				errors.add("No private key specified.");
				valid = false;
			}

			if(!valid) {
				return Optional.empty();
			}

			return Optional.of(new TransportFactory.Config(
					uri,
					user,
					hostKeys,
					privateKey,
					Optional.empty(),
					Optional.empty(),
					TransportFactory.readTunnels(cfg)
			));
		}
	};
}
