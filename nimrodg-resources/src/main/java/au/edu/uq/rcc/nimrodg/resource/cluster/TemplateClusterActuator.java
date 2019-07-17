package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType.BatchedClusterConfig;
import au.edu.uq.rcc.nimrodg.resource.cluster.pbs.PBSActuator;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TemplateClusterActuator extends PBSActuator /* for now */ {

	private final Jinjava jj;
	private final String template;

	public TemplateClusterActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, BatchedClusterConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		jj = new Jinjava();
		jj.getGlobalContext().registerFilter(new Filter() {
			@Override
			public Object filter(Object o, JinjavaInterpreter ji, String... strings) {
				if(o == null) {
					return null;
				}
				return ActuatorUtils.posixQuoteArgument(o.toString());
			}

			@Override
			public String getName() {
				return "quote";
			}
		});

		this.template = new String(Files.readAllBytes(Paths.get("/home/zane/Desktop/staging/tt/tinaroo.j2")), StandardCharsets.UTF_8);
	}

	@Override
	protected String buildSubmissionScript(UUID[] batchUuids) {
		Map<String, Object> agentVars = new HashMap<>();
		agentVars.put("amqp_uri", uri.uri);
		agentVars.put("amqp_routing_key", routingKey);
		agentVars.put("amqp_no_verify_peer", uri.noVerifyPeer);
		agentVars.put("amqp_no_verify_host", uri.noVerifyHost);
		this.remoteCertPath.ifPresent(p -> {
			agentVars.put("cacert", p);
			agentVars.put("caenc", "plain");
			agentVars.put("no_ca_delete", true);
		});
		agentVars.put("output", "workroot");
		agentVars.put("batch", false);

		Map<String, Object> vars = Map.of(
				"batch_size", batchUuids.length,
				"walltime", config.dialect.getWalltime(config.batchConfig).orElse(86400L), // FIXME:
				"agent_binary", this.remoteAgentPath,
				"agent_uuids", batchUuids,
				"agent_args", agentVars
		);

		return jj.render(template, vars);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		/* Set the walltime. */
		state.setExpiryTime(state.getConnectionTime().plusSeconds(config.dialect.getWalltime(config.batchConfig).orElse(86400L)));
	}
}
