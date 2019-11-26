package au.edu.uq.rcc.nimrodg.shell;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.shell.UnknownCommand;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

public class ShellTests {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private SshServer sshd;
    private KeyPair hostKey;
    private KeyPair keyPair;
    private URI uri;

    private Path keyPath;

    private FileSystem memFs;
    private Path memKeyPath;

    @Test
    public void sshdClientTest() throws IOException {
        try(SshdClient client = new SshdClient(uri, hostKey.getPublic(), keyPair)) {
            testClient(client);
        }
    }

    @Test
    public void opensshClientTest() throws IOException {
        /* Only test OpenSSH if its available. */
        Path openSsh = Paths.get("/usr/bin/ssh");
        if(!Files.exists(openSsh)) {
            System.err.printf("'%s' doesn't exist, skipping OpenSSH backend test...\n", openSsh);
            return;
        }

        /* Use the on-disk key. */
        try(OpenSSHClient client = new OpenSSHClient(uri, tmpDir.getRoot().toPath(), Optional.of(keyPath), Optional.of(openSsh), Map.of("StrictHostKeyChecking", "no"))) {
            testClient(client);
        }

        /* Use the in-memory key. */
        try(OpenSSHClient client = new OpenSSHClient(uri, tmpDir.getRoot().toPath(), Optional.of(memKeyPath), Optional.of(openSsh), Map.of("StrictHostKeyChecking", "no"))) {
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

        final ScpCommandFactory scpFactory;
        final FileSystem fs;

        _CommandFactory(FileSystem fs) {
            this.fs = fs;
            this.scpFactory = new ScpCommandFactory();
        }

        @Override
        public Command createCommand(ChannelSession channel, String cmd) throws IOException {
            String[] argv = ShellUtils.translateCommandline(cmd);
            if(argv.length == 0) {
                return new UnknownCommand(cmd);
            }

            switch(argv[0]) {
                case "scp":
                    return scpFactory.createCommand(channel, cmd);
                default:
                    return new OneShotCommand(cmd, fs);
            }
        }

    }

    private static void writePEM(Path path, PrivateKey pk) {
        try(OutputStream os = Files.newOutputStream(path)) {
            try(JcaPEMWriter pemw = new JcaPEMWriter(new OutputStreamWriter(os, StandardCharsets.US_ASCII))) {
                pemw.writeObject(pk);
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Before
    public void setup() throws IOException, GeneralSecurityException {
        /* https://docs.oracle.com/javase/7/docs/api/java/security/KeyPairGenerator.html */
        KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
        keygen.initialize(2048);
        keyPair = keygen.generateKeyPair();
        hostKey = keygen.generateKeyPair();

        keyPath = tmpDir.newFile("key").toPath();

        writePEM(keyPath, keyPair.getPrivate());
        Files.setPosixFilePermissions(keyPath, EnumSet.of(PosixFilePermission.OWNER_READ));

        memFs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build());
        memKeyPath = memFs.getPath("/key");
        writePEM(memKeyPath, keyPair.getPrivate());
        Files.setPosixFilePermissions(memKeyPath, EnumSet.of(PosixFilePermission.OWNER_READ));

        sshd = SshServer.setUpDefaultServer();
        /* Get a random, non-privileged port and hope it's not taken. */
        int port = (int)(Math.random() * (65535 - 1024)) + 1024;
        sshd.setPort(port);
        sshd.setKeyPairProvider(KeyPairProvider.wrap(hostKey));
        sshd.setPublickeyAuthenticator((user, key, ses) -> user.equals("user") && key.equals(keyPair.getPublic()));
        sshd.setCommandFactory(new _CommandFactory(memFs));
        sshd.setFileSystemFactory(new FileSystemFactoryWrapper(memFs));

        uri = URI.create(String.format("ssh://user@127.0.0.1:%d", sshd.getPort()));
        sshd.start();
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
