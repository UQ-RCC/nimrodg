package au.edu.uq.rcc.nimrodg.portal;

import au.edu.uq.rcc.nimrodg.setup.AMQPConfigBuilder;
import au.edu.uq.rcc.nimrodg.setup.SetupConfigBuilder;
import au.edu.uq.rcc.nimrodg.setup.TransferConfigBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import java.nio.file.Paths;
import java.util.Map;

/* This whole class is boilerplate to get application.yml config into Nimrod's SetupConfig. */
@ConfigurationProperties(prefix = "nimrod.setup")
@ConstructorBinding
public class DefaultSetupConfig {

	@ConstructorBinding
	private static class AMQP {
		final UriBuilder uri;
		final String routingKey;
		final String cert;
		final boolean noVerifyPeer;
		final boolean noVerifyHost;

		public AMQP(String uri, String routingKey, String cert, boolean noVerifyPeer, boolean noVerifyHost) {
			this.uri = new DefaultUriBuilderFactory().uriString(uri);
			this.routingKey = routingKey;
			this.cert = cert;
			this.noVerifyPeer = noVerifyPeer;
			this.noVerifyHost = noVerifyHost;
		}
	}

	@ConstructorBinding
	private static class Transfer {
		final UriBuilder uri;
		final String cert;
		final boolean noVerifyPeer;
		final boolean noVerifyHost;

		public Transfer(String uri, String cert, boolean noVerifyPeer, boolean noVerifyHost) {
			this.uri = new DefaultUriBuilderFactory().uriString(uri);
			this.cert = cert;
			this.noVerifyPeer = noVerifyPeer;
			this.noVerifyHost = noVerifyHost;
		}
	}

	private UriBuilder workDir;
	private UriBuilder storeDir;
	private AMQP amqp;
	private Transfer transfer;
	private Map<String, String> agents;
	private Map<String, Map<String, String>> agentMap;
	private Map<String, String> resourceTypes;
	private Map<String, String> properties;


	public DefaultSetupConfig(
			String workDir,
			String storeDir,
			AMQP amqp,
			Transfer transfer,
			Map<String, String> agents,
			Map<String, Map<String, String>> agentMap,
			Map<String, String> resourceTypes,
			Map<String, String> properties) {
		this.workDir = new DefaultUriBuilderFactory().uriString(workDir);
		this.storeDir = new DefaultUriBuilderFactory().uriString(storeDir);
		this.amqp = amqp;
		this.transfer = transfer;
		this.agents = agents;
		this.agentMap = agentMap;
		this.resourceTypes = resourceTypes;
		this.properties = properties;
	}

	public SetupConfigBuilder toBuilder(Map<String, String> substVars) {
		SetupConfigBuilder b = new SetupConfigBuilder()
				.workDir(workDir.build(substVars).getPath())
				.storeDir(storeDir.build(substVars).getPath())
				.amqp(new AMQPConfigBuilder()
						.uri(amqp.uri.build(substVars))
						.routingKey(amqp.routingKey)
						.certPath(amqp.cert)
						.noVerifyPeer(amqp.noVerifyPeer)
						.noVerifyHost(amqp.noVerifyHost)
						.build())
				.transfer(new TransferConfigBuilder()
						.uri(transfer.uri.build(substVars))
						.certPath(transfer.cert)
						.noVerifyPeer(transfer.noVerifyPeer)
						.noVerifyHost(transfer.noVerifyHost)
						.build())
				.resourceTypes(resourceTypes)
				.properties(properties);

		agentMap.forEach((s, x) -> x.forEach((m, plat) -> b.agentMapping(s, m, plat)));
		agents.forEach((plat, path) -> b.agent(plat, Paths.get(path)));
		return b;
	}
}
