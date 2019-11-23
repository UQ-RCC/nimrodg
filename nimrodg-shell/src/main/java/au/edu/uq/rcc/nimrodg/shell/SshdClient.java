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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
import org.apache.sshd.common.scp.ScpTimestamp;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshdClient implements RemoteShell {

	private static final Logger LOGGER = LoggerFactory.getLogger(SshdClient.class);

	private final URI uri;
	private final PublicKey[] hostKeys;
	private final KeyPair keyPair;
	private final SshClient client;
	private final ClientSession session;
	private final ScpClient scp;

	private boolean closed;

	public SshdClient(URI uri, PublicKey hostKey, KeyPair keyPair) throws IOException, IllegalArgumentException {
		this(uri, new PublicKey[]{hostKey}, keyPair);
	}

	public SshdClient(URI uri, PublicKey[] hostKeys, KeyPair keyPair) throws IOException, IllegalArgumentException {
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
		int operms = ShellUtils.permissionsToInt(perms);
		runCommand("chmod", Integer.toString(operms, 8), destPath);
	}

	public static CommandResult runCommand(ClientSession ses, String... args) throws IOException {
		ByteArrayOutputStream baosout = new ByteArrayOutputStream();
		ByteArrayOutputStream baoserr = new ByteArrayOutputStream();
		int ret;
		String cmdline = ShellUtils.buildEscapedCommandLine(args);

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

	@SuppressWarnings("empty-statement")
	public static PublicKey[] resolveHostKeysOld(String user, String host, int port) throws IOException {
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
				while(!cf.await())
					;

				assert cf.isDone();

				if(!cf.isConnected()) {
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

	public static PublicKey[] resolveHostKeys(String user, String host, int port) throws IOException {
		return resolveHostKeys(user, host, port, 0, 0);
	}

	public static PublicKey[] resolveHostKeys(String user, String host, int port, int retryCount, long retryMs) throws IOException {
		try {
			return resolveHostKeys(user, host, port, retryCount, retryMs, new AtomicBoolean(true));
		} catch(InterruptedException e) {
			/* Will never happen. */
			throw new IllegalStateException();
		}
	}

	@SuppressWarnings({"empty-statement", "SleepWhileInLoop"})
	public static PublicKey[] resolveHostKeys(String user, String host, int port, int retryCount, long retryMs, AtomicBoolean wantQuit) throws IOException, InterruptedException {
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

				ConnectFuture cf;
				int retries = retryCount;
				do {
					cf = ssh.connect(user, host, port < 0 ? 22 : port);

					for(boolean done = false; !done;) {
						try {
							done = cf.await();
						} catch(InterruptedIOException e) {
							if(wantQuit.get()) {
								throw new InterruptedException();
							}
						}
					}

					assert cf.isDone();

					if(cf.isConnected()) {
						break;
					}

					try {
						Thread.sleep(retryMs);
					} catch(InterruptedException ex) {
						/* nop */
						if(wantQuit.get()) {
							throw ex;
						}
					}

				} while(retries-- > 0);

				if(!cf.isConnected()) {
					Throwable t = cf.getException();
					if(t instanceof IOException) {
						throw (IOException)t;
					} else {
						throw new IOException(t);
					}
				}

				try(ClientSession ses = cf.getSession()) {
					AuthFuture af = ses.auth();

					for(boolean done = false; !done;) {
						try {
							done = af.await();
						} catch(InterruptedIOException e) {
							if(wantQuit.get()) {
								throw new InterruptedException();
							}
						}
					}
				}
			}
		}

		return pubKeys.stream().toArray(PublicKey[]::new);
	}
}
