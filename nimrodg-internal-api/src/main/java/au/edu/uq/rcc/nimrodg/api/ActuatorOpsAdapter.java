package au.edu.uq.rcc.nimrodg.api;

import au.edu.uq.rcc.nimrodg.api.*;

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
	public Map<String, AgentInfo> lookupAgents() {
		return nimrod.lookupAgents();
	}

	@Override
	public AgentInfo lookupAgentByPlatform(String platString) {
		return nimrod.lookupAgentByPlatform(platString);
	}

	@Override
	public AgentInfo lookupAgentByPosix(String system, String machine) {
		return nimrod.lookupAgentByPosix(system, machine);
	}
}