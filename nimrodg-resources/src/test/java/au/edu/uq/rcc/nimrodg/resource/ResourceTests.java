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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import org.junit.Assert;
import org.junit.Test;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.pbs.PBSProDialect;
import au.edu.uq.rcc.nimrodg.resource.cluster.slurm.SLURMDialect;
import au.edu.uq.rcc.nimrodg.resource.ssh.OpenSSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.test.TestUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class ResourceTests {

	public static final String DEFAULT_AGENT = "x86_64-pc-linux-musl";

	private static class _TestSSHResource extends SSHResourceType {

		public _TestSSHResource() {
			super("sshtest", "SSHTest");
		}

		@Override
		public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshCfg) throws IOException {
			throw new UnsupportedOperationException("Seriously?");
		}
	}

	private static class _AgentProvider implements AgentProvider {

		public static AgentProvider INSTANCE = new _AgentProvider();

		@Override
		public AgentInfo lookupAgentByPlatform(String platString) {
			return new AgentInfo() {
				@Override
				public String getPlatformString() {
					return DEFAULT_AGENT;
				}

				@Override
				public String getPath() {
					return "/some/path/agent";
				}

				@Override
				public List<Map.Entry<String, String>> posixMappings() {
					return List.of();
				}
			};
		}

		@Override
		public AgentInfo lookupAgentByPosix(String system, String machine) {
			return new AgentInfo() {
				@Override
				public String getPlatformString() {
					return DEFAULT_AGENT;
				}

				@Override
				public String getPath() {
					return "/some/path/agent";
				}

				@Override
				public List<Map.Entry<String, String>> posixMappings() {
					return List.of();
				}
			};
		}

	}

	@Rule
	public TemporaryFolder tmpDir = new TemporaryFolder();

	private Path pubKey;
	private Path privKey;
	private Optional<PublicKey> hostKey;
	private KeyPair keyPair;
	private X509Certificate x509;
	private byte[] rawX509;
	private Path x509Path;

	private static X509Certificate keyPairToCert(KeyPair keyPair) throws CertIOException, IOException, OperatorCreationException, CertificateException {
		X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
				.addRDN(BCStyle.CN, "nimrodtest")
				.build();

		byte[] id = new byte[20];
		Random r = new Random();
		r.nextBytes(id);
		BigInteger serial = new BigInteger(160, r);
		X509v3CertificateBuilder certificate = new JcaX509v3CertificateBuilder(
				subject,
				serial,
				new Date(Instant.EPOCH.toEpochMilli()),
				new Date(Instant.EPOCH.plus(365000, ChronoUnit.DAYS).toEpochMilli()),
				subject,
				keyPair.getPublic()
		);

		certificate.addExtension(Extension.subjectKeyIdentifier, false, id);
		certificate.addExtension(Extension.authorityKeyIdentifier, false, id);
		BasicConstraints constraints = new BasicConstraints(true);
		certificate.addExtension(
				Extension.basicConstraints,
				true,
				constraints.getEncoded());
		KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature);
		certificate.addExtension(Extension.keyUsage, false, usage.getEncoded());
		ExtendedKeyUsage usageEx = new ExtendedKeyUsage(new KeyPurposeId[]{
			KeyPurposeId.id_kp_serverAuth,
			KeyPurposeId.id_kp_clientAuth
		});
		certificate.addExtension(
				Extension.extendedKeyUsage,
				false,
				usageEx.getEncoded());

		ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
				.build(keyPair.getPrivate());
		X509CertificateHolder holder = certificate.build(signer);

		JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
		converter.setProvider(new BouncyCastleProvider());
		return converter.getCertificate(holder);
	}

	@Before
	public void before() throws IOException, OperatorCreationException, CertificateException {
		pubKey = tmpDir.newFile("key.pub").toPath();
		Files.write(pubKey, TestUtils.RSA_PEM_KEY_PUBLIC.getBytes(StandardCharsets.UTF_8));

		privKey = tmpDir.newFile("key").toPath();
		Files.write(privKey, TestUtils.RSA_PEM_KEY_PRIVATE.getBytes(StandardCharsets.UTF_8));

		List<String> errors = new ArrayList<>();
		hostKey = ActuatorUtils.validateHostKey(TestUtils.RSA_PEM_KEY_PUBLIC, errors);
		Assert.assertTrue(hostKey.isPresent());

		keyPair = ActuatorUtils.readPEMKey(privKey);

		x509 = keyPairToCert(keyPair);
		rawX509 = x509.getEncoded();

		x509Path = tmpDir.newFile("cert.crt").toPath();
		Files.write(x509Path, rawX509);
	}

	@After
	public void after() throws IOException {
		Files.delete(pubKey);
		Files.delete(privKey);
	}

	private void testSSHParser(SSHResourceType ssh, URI uri) {
		{
			String[] sshdArgs = {
				"--uri", uri.toString(),
				"--key", privKey.toString(),
				"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
				"--platform", DEFAULT_AGENT,
				"--transport", "sshd"
			};

			JsonStructure js = ssh.parseCommandArguments(_AgentProvider.INSTANCE, sshdArgs, System.out, System.err);
			Assert.assertNotNull(js);

			JsonObject cfg = js.asJsonObject();

			Assert.assertEquals(Set.of("transport", "agent_platform"), cfg.keySet());
			Assert.assertEquals(DEFAULT_AGENT, cfg.getString("agent_platform"));

			List<String> errors = new ArrayList<>();
			Optional<TransportFactory.Config> topt = SSHClient.FACTORY.validateConfiguration(cfg.getJsonObject("transport"), errors);
			Assert.assertTrue(topt.isPresent());

			TransportFactory.Config tcfg = topt.get();
			Assert.assertTrue(tcfg.uri.isPresent());
			Assert.assertEquals(uri, tcfg.uri.get());
			Assert.assertArrayEquals(new PublicKey[]{hostKey.get()}, tcfg.hostKeys);
			Assert.assertTrue(tcfg.privateKey.isPresent());
			Assert.assertEquals(privKey, tcfg.privateKey.get());
			Assert.assertFalse(tcfg.keyPair.isPresent());
			Assert.assertFalse(tcfg.executablePath.isPresent());
		}

		{

			String[] openSshArgs = {
				"--uri", uri.toString(),
				"--key", privKey.toString(),
				"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
				"--platform", DEFAULT_AGENT,
				"--transport", "openssh",
				"--openssh-executable", "/path/to/ssh"
			};

			JsonStructure js = ssh.parseCommandArguments(_AgentProvider.INSTANCE, openSshArgs, System.out, System.err);
			Assert.assertNotNull(js);

			JsonObject cfg = js.asJsonObject();

			Assert.assertEquals(Set.of("transport", "agent_platform"), cfg.keySet());
			Assert.assertEquals(DEFAULT_AGENT, cfg.getString("agent_platform"));

			List<String> errors = new ArrayList<>();
			Optional<TransportFactory.Config> topt = OpenSSHClient.FACTORY.validateConfiguration(cfg.getJsonObject("transport"), errors);
			Assert.assertTrue(topt.isPresent());

			TransportFactory.Config tcfg = topt.get();
			Assert.assertTrue(tcfg.uri.isPresent());
			Assert.assertEquals(uri, tcfg.uri.get());
			Assert.assertArrayEquals(new PublicKey[0], tcfg.hostKeys);
			Assert.assertTrue(tcfg.privateKey.isPresent());
			Assert.assertEquals(privKey, tcfg.privateKey.get());
			Assert.assertFalse(tcfg.keyPair.isPresent());
			Assert.assertTrue(tcfg.executablePath.isPresent());
			Assert.assertEquals(tcfg.executablePath.get(), Paths.get("/path/to/ssh"));
		}

	}

	@Test
	public void sshBaseParserTests() {
		SSHResourceType ssh = new _TestSSHResource();
		URI validSshUri = URI.create("ssh://username@hostname:22");
		testSSHParser(ssh, validSshUri);
	}

	@Test
	public void sshBaseParserURIWithPasswordTest() {
		SSHResourceType ssh = new _TestSSHResource();
		JsonStructure js = ssh.parseCommandArguments(null, new String[]{
			"--uri", "ssh://username:pass@hostname:22",
			"--key", "",
			"--hostkey", "none"
		}, System.out, System.err);

		Assert.assertNull(js);
	}

	@Test
	public void pbsProBatchArgsTest() {
		JsonObject expected;
		try(JsonReader p = Json.createReader(new StringReader("{\"agent_platform\":\"x86_64-pc-linux-musl\",\"transport\":{\"name\":\"sshd\",\"uri\":\"ssh://user@pbscluster.com\",\"keyfile\":\"/path/to/key\",\"hostkeys\":[\"ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDMIAQc5QFZfdjImP2T9FNGe9r6l89binb5uH/vxzlnhAtHxesD8B7WXFBN/GxOplb3ih/vadT9gWliXUayvMn+ZMO7iBScnZwdmcMeKP3K80Czlrio+eI3jU77RQPYXBtcD8CBRT94r7nd29I+lMWxOD1U+LBA43kxAbyXqkQ0PQ==\"]},\"tmpvar\":\"TMPDIR\",\"pbsargs\":[\"-A\",\"ACCOUNTSTRING\"],\"limit\":100,\"max_batch_size\":10,\"batch_config\":[{\"name\":\"walltime\",\"value\":36000,\"scale\":false},{\"name\":\"pmem\",\"value\":2000000000000,\"scale\":false},{\"name\":\"pvmem\",\"value\":1000000000000,\"scale\":false},{\"name\":\"ncpus\",\"value\":1,\"scale\":true},{\"name\":\"mem\",\"value\":1073741824,\"scale\":true},{\"name\":\"vmem\",\"value\":536870912000,\"scale\":true},{\"name\":\"mpiprocs\",\"value\":1,\"scale\":true},{\"name\":\"ompthreads\",\"value\":1,\"scale\":true}]}"))) {
			expected = p.readObject();
		}

		PBSProResourceType pbs = new PBSProResourceType();
		URI sshUri = URI.create("ssh://user@pbscluster.com");
		String keyPath = "/path/to/key";

		String[] pbsargs = {"-A", "ACCOUNTSTRING"};

		String[] args = Stream.concat(
				Stream.of(
						"--uri", sshUri.toString(),
						"--key", keyPath,
						"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
						"--tmpvar", "TMPDIR",
						"--limit", "100",
						"--platform", DEFAULT_AGENT,
						"--max-batch-size", "10",
						"--add-batch-res-scale", "ncpus:1",
						"--add-batch-res-scale", "mem:1GiB",
						"--add-batch-res-scale", "vmem:500GiB",
						"--add-batch-res-scale", "mpiprocs:1",
						"--add-batch-res-scale", "ompthreads:1",
						"--add-batch-res-static", "walltime:10:00:00",
						"--add-batch-res-static", "pmem:2TB",
						"--add-batch-res-static", "pvmem:1TB",
						"--no-validate-private-key",
						"--"
				),
				Stream.of(pbsargs)
		).toArray(String[]::new);

		JsonObject js = (JsonObject)pbs.parseCommandArguments(_AgentProvider.INSTANCE, args, System.out, System.err);
		Assert.assertEquals(expected, js);

		PBSProDialect d = new PBSProDialect();
		String[] subArgs = d.buildSubmissionArguments(5, js.getJsonArray("batch_config").stream().map(j -> (JsonObject)j).toArray(JsonObject[]::new), pbsargs);
		String[] expectedArgs = new String[]{
			"-A", "ACCOUNTSTRING",
			"-l", "walltime=36000",
			"-l", "pmem=2000000000000b",
			"-l", "pvmem=1000000000000b",
			"-l", "select=1:ncpus=5:mem=5368709120b:vmem=2684354560000b:mpiprocs=5:ompthreads=5"
		};

		Assert.assertArrayEquals(expectedArgs, subArgs);
	}

	@Test
	public void slurmBatchArgsTest() {
		JsonObject expected;
		try(JsonReader p = Json.createReader(new StringReader("{\"agent_platform\":\"x86_64-pc-linux-musl\",\"transport\":{\"name\":\"sshd\",\"uri\":\"ssh://user@pbscluster.com\",\"keyfile\":\"/path/to/key\",\"hostkeys\":[\"ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDMIAQc5QFZfdjImP2T9FNGe9r6l89binb5uH/vxzlnhAtHxesD8B7WXFBN/GxOplb3ih/vadT9gWliXUayvMn+ZMO7iBScnZwdmcMeKP3K80Czlrio+eI3jU77RQPYXBtcD8CBRT94r7nd29I+lMWxOD1U+LBA43kxAbyXqkQ0PQ==\"]},\"tmpvar\":\"TMPDIR\",\"slurmargs\":[\"--job-name\",\"NimrodTest\"],\"limit\":100,\"max_batch_size\":10,\"batch_config\":[{\"name\":\"cpus-per-task\",\"value\":1,\"scale\":false},{\"name\":\"nodes\",\"value\":1,\"scale\":false},{\"name\":\"mem-per-cpu\",\"value\":1073741824,\"scale\":false},{\"name\":\"ntasks-per-node\",\"value\":1,\"scale\":false},{\"name\":\"ntasks\",\"value\":1,\"scale\":true}]}"))) {
			expected = p.readObject();
		}

		SLURMResourceType slurm = new SLURMResourceType();
		URI sshUri = URI.create("ssh://user@pbscluster.com");
		String keyPath = "/path/to/key";

		String[] slurmargs = {"--job-name", "NimrodTest"};

		String[] args = Stream.concat(
				Stream.of(
						"--uri", sshUri.toString(),
						"--key", keyPath,
						"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
						"--tmpvar", "TMPDIR",
						"--limit", "100",
						"--platform", DEFAULT_AGENT,
						"--max-batch-size", "10",
						"--add-batch-res-static", "cpus-per-task:1",
						"--add-batch-res-static", "nodes:1",
						"--add-batch-res-scale", "ntasks:1",
						"--add-batch-res-static", "mem-per-cpu:1GiB",
						"--add-batch-res-static", "ntasks-per-node:1",
						"--no-validate-private-key",
						"--"
				),
				Stream.of(slurmargs)
		).toArray(String[]::new);

		JsonObject js = (JsonObject)slurm.parseCommandArguments(_AgentProvider.INSTANCE, args, System.out, System.err);
		Assert.assertEquals(expected, js);

		SLURMDialect sd = new SLURMDialect();
		String[] subArgs = sd.buildSubmissionArguments(5, js.getJsonArray("batch_config").stream().map(j -> (JsonObject)j).toArray(JsonObject[]::new), slurmargs);
		String[] expectedArgs = new String[]{
			"--cpus-per-task", "1",
			"--nodes", "1",
			"--mem-per-cpu", "1073741K",
			"--ntasks-per-node", "1",
			"--ntasks", "5",
			"--job-name", "NimrodTest"
		};

		Assert.assertArrayEquals(expectedArgs, subArgs);
	}

	@Test
	public void pbsParserTest() {
		JsonObject expected;
		try(JsonReader p = Json.createReader(new StringReader("{\"agent_platform\":\"x86_64-pc-linux-musl\",\"transport\":{\"name\":\"sshd\",\"uri\":\"ssh://user@pbscluster.com\",\"keyfile\":\"/path/to/key\",\"hostkeys\":[\"ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDMIAQc5QFZfdjImP2T9FNGe9r6l89binb5uH/vxzlnhAtHxesD8B7WXFBN/GxOplb3ih/vadT9gWliXUayvMn+ZMO7iBScnZwdmcMeKP3K80Czlrio+eI3jU77RQPYXBtcD8CBRT94r7nd29I+lMWxOD1U+LBA43kxAbyXqkQ0PQ==\"]},\"tmpvar\":\"TMPDIR\",\"pbsargs\":[\"-A\",\"ACCOUNTSTRING\",\"-l\",\"select=1:ncpus=1,pmem=1gb\",\"-l\",\"walltime=10:00:00\"],\"limit\":100,\"max_batch_size\":10,\"batch_config\":[]}"))) {
			expected = p.readObject();
		}

		PBSProResourceType pbs = new PBSProResourceType();
		URI sshUri = URI.create("ssh://user@pbscluster.com");

		String[] pbsargs = {
			"-A", "ACCOUNTSTRING",
			"-l", "select=1:ncpus=1,pmem=1gb",
			"-l", "walltime=10:00:00"
		};

		String[] args = Stream.concat(
				Stream.of(
						"--uri", sshUri.toString(),
						"--key", "/path/to/key",
						"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
						//"--tmpvar", "TMPDIR", /* Should default to TMPDIR */
						"--limit", "100",
						"--platform", DEFAULT_AGENT,
						"--no-validate-private-key",
						"--"
				), Arrays.stream(pbsargs)
		).toArray(String[]::new);

		JsonStructure js = pbs.parseCommandArguments(_AgentProvider.INSTANCE, args, System.out, System.err);
		Assert.assertEquals(expected, js);
	}

	@Test
	public void certificateTests() throws IOException, CertificateException {
		Certificate[] emptyCerts = new Certificate[0];
		Assert.assertArrayEquals(emptyCerts, ActuatorUtils.readX509Certificates((String)null));
		Assert.assertArrayEquals(emptyCerts, ActuatorUtils.readX509Certificates(""));

		Certificate[] cert = ActuatorUtils.readX509Certificates(x509Path);
		Assert.assertArrayEquals(new Certificate[]{x509}, cert);
	}

}
