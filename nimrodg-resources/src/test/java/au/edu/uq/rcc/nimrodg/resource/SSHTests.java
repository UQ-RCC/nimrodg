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
package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.OpenSSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHClient;
import au.edu.uq.rcc.nimrodg.test.TestUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Assert;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.shell.UnknownCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SSHTests {

	@Rule
	public TemporaryFolder tmpDir = new TemporaryFolder();

	private SshServer sshd;
	private PublicKey hostKey;
	private KeyPair keyPair;
	private URI uri;

	private Path keyPath;

	private FileSystem memFs;
	private Path memKeyPath;

	@Test
	public void sshdClientTest() throws IOException {
		try(SSHClient client = new SSHClient(uri, hostKey, keyPair)) {
			testClient(client);
		}
	}

	@Test
	public void opensshClientTest() throws IOException {
		/* Only test OpenSSH if its available. */
		Path openSsh = Paths.get("/usr/bin/ssh");
		if(!Files.exists(keyPath)) {
			System.err.printf("'%s' doesn't exist, skipping OpenSSH backend test...\n", openSsh);
			return;
		}

		try(OpenSSHClient client = new OpenSSHClient(uri, Optional.of(keyPath), openSsh.toString())) {
			testClient(client);
		}
	}

	private void testClient(RemoteShell client) throws IOException {
		RemoteShell.CommandResult cr = client.runCommand("echo", "asdf");
		Assert.assertEquals(0, cr.status);
		Assert.assertEquals("asdf\n", cr.stdout);
		Assert.assertEquals("", cr.stderr);

		byte[] payload = new byte[]{'a', 's', 'd', 'f', 0x0D, 0x0A};
		Path asdfPath = memFs.getPath("/asdf");
		client.upload(asdfPath.toString(), payload, EnumSet.of(PosixFilePermission.OWNER_READ), Instant.now());
		Assert.assertArrayEquals(payload, Files.readAllBytes(asdfPath));
		Assert.assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ), Files.getPosixFilePermissions(asdfPath));
	}

	private static class _CommandFactory implements CommandFactory {

		public final ScpCommandFactory scpFactory;
		public final FileSystem fs;

		public _CommandFactory(FileSystem fs) {
			this.fs = fs;
			this.scpFactory = new ScpCommandFactory();
		}

		@Override
		public Command createCommand(String cmd) {
			String[] argv = ActuatorUtils.translateCommandline(cmd);
			if(argv.length == 0) {
				return new UnknownCommand(cmd);
			}

			switch(argv[0]) {
				case "scp":
					return scpFactory.createCommand(cmd);
				default:
					return new OneShotCommand(cmd, fs);
			}
		}

	}

	@Before
	public void setup() throws IOException, GeneralSecurityException {
		keyPath = tmpDir.newFile("key").toPath();
		Files.write(keyPath, TestUtils.RSA_PEM_KEY_PRIVATE.getBytes(StandardCharsets.UTF_8));
		Files.setPosixFilePermissions(keyPath, EnumSet.of(PosixFilePermission.OWNER_READ));

		memFs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build());
		memKeyPath = memFs.getPath("/key");
		Files.write(memKeyPath, TestUtils.RSA_PEM_KEY_PRIVATE.getBytes(StandardCharsets.UTF_8));
		Files.setPosixFilePermissions(memKeyPath, EnumSet.of(PosixFilePermission.OWNER_READ));

		sshd = SshServer.setUpDefaultServer();
		int port = (int)(Math.random() * (65535 - 1024)) + 1024;
		//int port = 6841;
		sshd.setPort(port);

		keyPair = ActuatorUtils.readPEMKey(TestUtils.RSA_PEM_KEY_PRIVATE);
		hostKey = ActuatorUtils.loadHostKey(TestUtils.RSA_PEM_KEY_PUBLIC);
		sshd.setKeyPairProvider(KeyPairProvider.wrap(keyPair));
		sshd.setPublickeyAuthenticator((user, key, ses) -> user.equals("user") && key.equals(keyPair.getPublic()));

		sshd.setCommandFactory(new _CommandFactory(memFs));
			sshd.setFileSystemFactory(ses -> {
			return new FileSystem() {
				@Override
				public FileSystemProvider provider() {
					return memFs.provider();
				}

				@Override
				public void close() throws IOException {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean isOpen() {
					return memFs.isOpen();
				}

				@Override
				public boolean isReadOnly() {
					return memFs.isReadOnly();
				}

				@Override
				public String getSeparator() {
					return memFs.getSeparator();
				}

				@Override
				public Iterable<Path> getRootDirectories() {
					return memFs.getRootDirectories();
				}

				@Override
				public Iterable<FileStore> getFileStores() {
					return memFs.getFileStores();
				}

				@Override
				public Set<String> supportedFileAttributeViews() {
					return memFs.supportedFileAttributeViews();
				}

				@Override
				public Path getPath(String arg0, String... arg1) {
					return memFs.getPath(arg0, arg1);
				}

				@Override
				public PathMatcher getPathMatcher(String arg0) {
					return memFs.getPathMatcher(arg0);
				}

				@Override
				public UserPrincipalLookupService getUserPrincipalLookupService() {
					return memFs.getUserPrincipalLookupService();
				}

				@Override
				public WatchService newWatchService() throws IOException {
					return memFs.newWatchService();
				}
			};
		});

		uri = URI.create(String.format("ssh://user@127.0.0.1:%d", sshd.getPort()));
		sshd.start();
	}

	public static void main(String[] args) throws IOException, GeneralSecurityException {
		SSHTests ssh = new SSHTests();
		ssh.setup();
		ssh.sshdClientTest();
		ssh.shutdown();
	}

	@After
	public void shutdown() throws IOException {
		if(sshd != null) {
			sshd.stop(true);
		}

		if(memFs != null) {
			memFs.close();
		}

	}
}
