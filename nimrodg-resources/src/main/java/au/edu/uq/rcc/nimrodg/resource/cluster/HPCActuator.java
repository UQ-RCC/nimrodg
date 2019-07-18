package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.HPCResourceType.HPCConfig;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HPCActuator extends ClusterActuator<HPCConfig> {

	private final Jinjava jj;

	public HPCActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, HPCConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		this.jj = createTemplateEngine();
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
				"walltime", 86400, // FIXME:
				"agent_binary", this.remoteAgentPath,
				"agent_uuids", batchUuids,
				"agent_args", agentVars
		);

		return jj.render(config.template, vars);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		/* Set the walltime. */
		state.setExpiryTime(state.getConnectionTime().plusSeconds(86400L)); // FIXME:
	}

	@Override
	protected String submitBatch(RemoteShell shell, TempBatch batch) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	protected boolean killJob(RemoteShell shell, String jobId) {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	public static Jinjava createTemplateEngine() {
		Jinjava jj = new Jinjava();
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
		return jj;
	}
}
