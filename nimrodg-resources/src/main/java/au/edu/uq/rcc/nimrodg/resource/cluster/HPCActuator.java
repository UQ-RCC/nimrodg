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
	protected String buildSubmissionScript(UUID[] batchUuids, String out, String err) {
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

		return jj.render(config.template, Map.of(
				"batch_size", batchUuids.length,
				"output_path", out,
				"error_path", err,
				"walltime", 86400, // FIXME:
				"agent_binary", this.remoteAgentPath,
				"agent_uuids", batchUuids,
				"agent_args", agentVars
		));
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

	public static Map<String, Object> createSampleVars() {
		UUID[] uuids = {
			UUID.fromString("a0ffd1ac-db63-4c6e-b661-f9c9349afccd"),
			UUID.fromString("09814659-2674-4180-aa60-d7a2ebcc26fa"),
			UUID.fromString("3aa4bd16-d425-4171-ae6d-bc5294289d82"),
			UUID.fromString("63203256-fa7a-4ff0-b336-e0b3fea4808c"),
			UUID.fromString("dfe82a4b-e295-4128-9180-afd83eb1c756"),
			UUID.fromString("98028d6e-881b-4ae0-aff7-199a8dd26fea"),
			UUID.fromString("94e98f08-0a44-4693-8202-447377e58f65"),
			UUID.fromString("8752a071-4bac-4508-9881-49b70e2fa6ae"),
			UUID.fromString("c67de3a0-ec19-4780-b533-828999482e3a"),
			UUID.fromString("73f5c6e7-afca-466d-aba6-b85aec2c93da")
		};
		return Map.of(
				"batch_size", uuids.length,
				"output_path", "/remote/path/to/stdout.txt",
				"error_path", "/remote/path/to/stderr.txt",
				"walltime", "86400",
				"agent_binary", "/remote/path/to/agent/binary",
				"agent_uuids", uuids,
				"agent_args", Map.of(
						"amqp_uri", "amqps://user:pass@host:port/vhost",
						"amqp_routing_key", "routingkey",
						"amqp_no_verify_peer", true,
						"amqp_no_verify_host", false,
						"cacert", "/path/to/cert.pem",
						"caenc", "b64",
						"no_ca_delete", true
				)
		);
	}
}
