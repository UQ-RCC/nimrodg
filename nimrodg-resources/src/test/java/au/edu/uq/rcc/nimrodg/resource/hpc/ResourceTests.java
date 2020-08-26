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
package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.MasterResourceType;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.TestShell;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.ClientFactories;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.test.TestAgentProvider;
import au.edu.uq.rcc.nimrodg.test.TestNimrodConfig;
import au.edu.uq.rcc.nimrodg.test.TestResource;
import au.edu.uq.rcc.nimrodg.test.TestUtils;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class ResourceTests {

	public static final String DEFAULT_AGENT = "x86_64-pc-linux-musl";

	private FileSystem memFs;
	private Path fsRoot;

	private Path pubKey;
	private Path privKey;
	private Optional<PublicKey> hostKey;
	private KeyPair keyPair;
	private X509Certificate x509;
	private byte[] rawX509;
	private Path x509Path;

	private AgentProvider agentProvider;
	private NimrodConfig nimrodConfig;

	private Resource testSSHResource;
	private Resource testClusterResource;

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
		memFs = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build());
		fsRoot = memFs.getPath("/");

		pubKey = fsRoot.resolve("key.pub");
		Files.write(pubKey, TestUtils.RSA_PEM_KEY_PUBLIC.getBytes(StandardCharsets.UTF_8));

		privKey = fsRoot.resolve("key");
		Files.write(privKey, TestUtils.RSA_PEM_KEY_PRIVATE.getBytes(StandardCharsets.UTF_8));

		List<String> errors = new ArrayList<>();
		hostKey = ActuatorUtils.validateHostKey(TestUtils.RSA_PEM_KEY_PUBLIC, errors);
		Assert.assertTrue(hostKey.isPresent());

		keyPair = ActuatorUtils.readPEMKey(privKey);

		x509 = keyPairToCert(keyPair);
		rawX509 = x509.getEncoded();

		x509Path = fsRoot.resolve("cert.crt");
		Files.write(x509Path, rawX509);

		agentProvider = new TestAgentProvider(fsRoot);
		nimrodConfig = new TestNimrodConfig(fsRoot);

		/* Create placeholder files for the agents. */
		for(AgentInfo ai : agentProvider.lookupAgents().values()) {
			Path p = ai.getAgentPath();
			Files.createDirectories(p.getParent());
			Files.write(p, new byte[0]);
		}

		testSSHResource = new TestResource(
				"testssh",
				new _TestSSHResourceType("testssh", "TestSSH"),
				nimrodConfig.getAmqpUri(),
				nimrodConfig.getTransferUri(),
				JsonValue.EMPTY_JSON_OBJECT
		);

		HPCConfig ccfg = new HPCConfig(
				new SSHResourceType.SSHConfig(
						agentProvider.lookupAgentByPlatform(DEFAULT_AGENT),
						TestShell.createFactory(fsRoot.resolve("home")),
						TestShell.createConfig(),
						List.of()
				),
				10,
				"TMPDIR",
				3,
				1,
				StringUtils.parseMemory("1GiB"),
				StringUtils.parseWalltime("24:00:00"),
				"UQ-X",
				"workq",
				"tinmgr2",
				new HPCDefinition(
						"dummy",
						new SubmitCommand.Builder()
								.argv("qsub", "{{ script_path }}")
								.parser(new LineRegexParser(Pattern.compile("^(?<jobid>.+)$"), "jobid", 1))
								.build(),
						new DeleteCommand.Builder()
								.argv("qdel")
								.appendJobIds(true)
								.parser(new NoopParser())
								.build(),
						new DeleteCommand.Builder()
								.argv("qdel", "-Wforce")
								.appendJobIds(true)
								.parser(new NoopParser())
								.build(),
						new QueryCommand.Builder()
								.argv("qstat")
								.appendJobIds(true)
								.parser(new NoopParser())
								.build(),
						""
				)
		);

		testClusterResource = new TestResource(
				"testcluster",
				new HPCResourceType() {
					@Override
					protected TransportFactory createTransportFactory(String name) {
						assert TestShell.TEST_TRANSPORT_NAME.equals(name);
						return TestShell.createFactory(fsRoot.resolve("home"));
					}
				},
				nimrodConfig.getAmqpUri(),
				nimrodConfig.getTransferUri(),
				ccfg.toJson()
		);
	}

	private static class _TestSSHResourceType extends SSHResourceType {
		_TestSSHResourceType(String name, String displayName) {
			super(name, displayName);
		}

		@Override
		protected Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, SSHConfig sshCfg) {
			throw new UnsupportedOperationException("really?");
		}
	}

	private void testSSHParser(ResourceType ssh, URI uri) {
		{
			String[] sshdArgs = {
					"--uri", uri.toString(),
					"--key", privKey.toUri().toString(),
					"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
					"--platform", DEFAULT_AGENT,
					"--transport", "sshd"
			};

			JsonStructure js = ssh.parseCommandArguments(agentProvider, sshdArgs, System.out, System.err, new Path[0]);
			Assert.assertNotNull(js);

			JsonObject cfg = js.asJsonObject();

			Assert.assertEquals(Set.of("transport", "agent_platform", "forwarded_environment"), cfg.keySet());
			Assert.assertEquals(DEFAULT_AGENT, cfg.getString("agent_platform"));

			List<String> errors = new ArrayList<>();
			Optional<TransportFactory.Config> topt = ClientFactories.SSHD_FACTORY.validateConfiguration(cfg.getJsonObject("transport"), errors);
			Assert.assertTrue(topt.isPresent());

			TransportFactory.Config tcfg = topt.get();
			Assert.assertTrue(tcfg.uri.isPresent());
			Assert.assertEquals(uri, tcfg.uri.get());
			Assert.assertArrayEquals(new PublicKey[]{hostKey.get()}, tcfg.hostKeys);
			Assert.assertTrue(tcfg.privateKey.isPresent());
			Assert.assertEquals(privKey, tcfg.privateKey.get());
			Assert.assertFalse(tcfg.executablePath.isPresent());
		}

		{

			String[] openSshArgs = {
					"--uri", uri.toString(),
					"--key", privKey.toUri().toString(),
					"--hostkey", TestUtils.RSA_PEM_KEY_PUBLIC,
					"--platform", DEFAULT_AGENT,
					"--transport", "openssh",
					"--openssh-executable", "/path/to/ssh",
					"--no-validate-private-key"
			};

			JsonStructure js = ssh.parseCommandArguments(agentProvider, openSshArgs, System.out, System.err, new Path[0]);
			Assert.assertNotNull(js);

			JsonObject cfg = js.asJsonObject();

			Assert.assertEquals(Set.of("transport", "agent_platform", "forwarded_environment"), cfg.keySet());
			Assert.assertEquals(DEFAULT_AGENT, cfg.getString("agent_platform"));

			List<String> errors = new ArrayList<>();
			Optional<TransportFactory.Config> topt = ClientFactories.OPENSSH_FACTORY.validateConfiguration(cfg.getJsonObject("transport"), errors);
			Assert.assertTrue(topt.isPresent());

			TransportFactory.Config tcfg = topt.get();
			Assert.assertTrue(tcfg.uri.isPresent());
			Assert.assertEquals(uri, tcfg.uri.get());
			Assert.assertArrayEquals(new PublicKey[0], tcfg.hostKeys);
			Assert.assertTrue(tcfg.privateKey.isPresent());
			Assert.assertEquals(privKey, tcfg.privateKey.get());
			Assert.assertTrue(tcfg.executablePath.isPresent());
			Assert.assertEquals(tcfg.executablePath.get(), Paths.get("/path/to/ssh"));
		}

	}

	@Test
	public void sshBaseParserTests() {
		URI validSshUri = URI.create("ssh://username@hostname:22");
		testSSHParser(testSSHResource.getType(), validSshUri);
	}

	@Test
	public void sshBaseParserURIWithPasswordTest() {
		JsonStructure js = testSSHResource.getType().parseCommandArguments(agentProvider, new String[]{
				"--uri", "ssh://username:pass@hostname:22",
				"--key", "",
				"--hostkey", "none"
		}, System.out, System.err, new Path[0]);

		Assert.assertNull(js);
	}

	@Test
	public void certificateTests() throws IOException, CertificateException {
		Certificate[] emptyCerts = new Certificate[0];
		Assert.assertArrayEquals(emptyCerts, ActuatorUtils.readX509Certificates((String)null));
		Assert.assertArrayEquals(emptyCerts, ActuatorUtils.readX509Certificates(""));

		Certificate[] cert = ActuatorUtils.readX509Certificates(x509Path);
		Assert.assertArrayEquals(new Certificate[]{x509}, cert);
	}

	private class _TestOps implements Actuator.Operations {
		public int agentCount = 0;

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {

		}

		@Override
		public NimrodConfig getConfig() {
			return nimrodConfig;
		}

		@Override
		public int getAgentCount(Resource res) {
			return agentCount;
		}

		@Override
		public Map<String, AgentInfo> lookupAgents() {
			return agentProvider.lookupAgents();
		}

		@Override
		public AgentInfo lookupAgentByPlatform(String platString) {
			return agentProvider.lookupAgentByPlatform(platString);
		}

		@Override
		public AgentInfo lookupAgentByPosix(String system, String machine) {
			return agentProvider.lookupAgentByPosix(system, machine);
		}
	}

	@Test
	public void clusterBatchTest() throws IOException {
		_TestOps ops = new _TestOps();

		MasterResourceType type = (MasterResourceType)testClusterResource.getType();

		Actuator.Request[] requests = new Actuator.Request[10];
		for(int i = 0; i < requests.length; ++i) {
			requests[i] = Actuator.Request.forAgent(UUID.randomUUID());
		}

		try(Actuator act = type.createActuator(ops, testClusterResource, nimrodConfig.getAmqpUri(), new Certificate[0])) {
			Actuator.LaunchResult[] lrs = act.launchAgents(requests);

			for(int i = 0; i < lrs.length; ++i) {
				if(lrs[i].t != null) {
					++ops.agentCount;
				}
			}

			for(int i = 0; i < lrs.length; ++i) {
				Assert.assertNotNull(lrs[i].actuatorData);
				Assert.assertTrue(lrs[i].actuatorData.containsKey("batch_id"));
				Assert.assertTrue(lrs[i].actuatorData.containsKey("batch_size"));
				Assert.assertTrue(lrs[i].actuatorData.containsKey("batch_index"));
			}

			Map<String, List<Actuator.LaunchResult>> l2rs = NimrodUtils.mapToParent(Arrays.stream(lrs),
					lr -> lr.actuatorData.getString("batch_id")
			);

			int[] sizes = l2rs.values().stream().mapToInt(List::size).sorted().toArray();
			Assert.assertArrayEquals(new int[]{1, 3, 3, 3}, sizes);

			for(Map.Entry<String, List<Actuator.LaunchResult>> e : l2rs.entrySet()) {
				for(Actuator.LaunchResult lr : e.getValue()) {
					Assert.assertEquals(e.getValue().size(), lr.actuatorData.getInt("batch_size"));
				}
			}
		}
	}
}
