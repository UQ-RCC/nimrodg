package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.LegacyClusterResourceType.DialectConfig;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.UUID;

public abstract class LegacyClusterActuator extends ClusterActuator<DialectConfig> {

	protected LegacyClusterActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, DialectConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
	}

	/**
	 * Apply the processed submission arguments to the job script.
	 *
	 * @param sb The submission script. This is just after the crunchbang.
	 * @param uuids The list of UUIDs being submitted.
	 * @param processedArgs The submission arguments after being processed by the dialect.
	 */
	protected abstract void applyBatchedSubmissionArguments(StringBuilder sb, UUID[] uuids, String[] processedArgs);

	/**
	 * Apply the submission arguments to the job script.
	 *
	 * @param sb The submission script. This is just after the crunchbang.
	 * @param uuids The list of UUIDs being submitted.
	 */
	private void applySubmissionArguments(StringBuilder sb, UUID[] uuids) {
		String[] args = config.dialect.buildSubmissionArguments(uuids.length, config.batchConfig, config.submissionArgs);
		applyBatchedSubmissionArguments(sb, uuids, args);
	}

	@Override
	protected String buildSubmissionScript(UUID[] batchUuids) {
		return ActuatorUtils.posixBuildSubmissionScriptMulti(
				batchUuids,
				String.format("$%s", config.tmpVar),
				uri,
				routingKey,
				this.remoteAgentPath,
				this.remoteCertPath,
				false,
				true,
				this::applySubmissionArguments
		);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		super.notifyAgentConnection(state);

		/* Set the walltime. */
		config.dialect.getWalltime(config.batchConfig)
				.ifPresent(l -> state.setExpiryTime(state.getConnectionTime().plusSeconds(l)));
	}
}
