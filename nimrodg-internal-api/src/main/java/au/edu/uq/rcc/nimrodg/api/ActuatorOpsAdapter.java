package au.edu.uq.rcc.nimrodg.api;

import java.util.Map;

public abstract class ActuatorOpsAdapter implements Actuator.Operations {
	private final NimrodMasterAPI nimrod;

	public ActuatorOpsAdapter(NimrodMasterAPI nimrod) {
		this.nimrod = nimrod;
	}

	@Override
	public NimrodConfig getConfig() {
		return nimrod.getConfig();
	}

	@Override
	public int getAgentCount(Resource res) {
		return nimrod.getResourceAgents(res).size();
	}

	@Override
	public Map<String, AgentDefinition> lookupAgents() {
		return nimrod.lookupAgents();
	}

	@Override
	public AgentDefinition lookupAgentByPlatform(String platString) {
		return nimrod.lookupAgentByPlatform(platString);
	}

	@Override
	public AgentDefinition lookupAgentByPosix(MachinePair pair) {
		return nimrod.lookupAgentByPosix(pair);
	}
}