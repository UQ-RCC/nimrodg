package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;

public class NoopAgentListener implements ReferenceAgent.AgentListener {
	@Override
	public void send(Agent agent, AgentMessage.Builder<?> msg) {

	}

	@Override
	public void onStateChange(Agent agent, AgentInfo.State oldState, AgentInfo.State newState) {

	}

	@Override
	public void onJobSubmit(Agent agent, NetworkJob job) {

	}

	@Override
	public void onJobUpdate(Agent agent, AgentUpdate au) {

	}

	@Override
	public void onPong(Agent agent, AgentPong pong) {

	}
}
