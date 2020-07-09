package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;

public class NoopAgentListener implements ReferenceAgent.AgentListener {
	@Override
	public void send(Agent agent, AgentMessage msg) {

	}

	@Override
	public void onStateChange(Agent agent, Agent.State oldState, Agent.State newState) {

	}

	@Override
	public void onJobSubmit(Agent agent, AgentSubmit as) {

	}

	@Override
	public void onJobUpdate(Agent agent, AgentUpdate au) {

	}

	@Override
	public void onPong(Agent agent, AgentPong pong) {

	}
}
