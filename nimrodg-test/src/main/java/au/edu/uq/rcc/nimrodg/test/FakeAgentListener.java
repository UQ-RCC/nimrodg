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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import java.io.IOException;
import java.time.Instant;

public class FakeAgentListener implements ReferenceAgent.AgentListener {

	public final DummyActuator actuator;
	public final NimrodMasterAPI nimrod;

	public FakeAgentListener(NimrodMasterAPI nimrod, DummyActuator act) {
		this.nimrod = nimrod;
		this.actuator = act;
	}

	@Override
	public void send(Agent agent, AgentMessage msg) throws IOException {

	}

	@Override
	public void onStateChange(Agent _agent, Agent.State oldState, Agent.State newState) {
		if(oldState == null) {
			/* Initial state setup, we don't do anything here. */
			return;
		}

		ReferenceAgent agent = (ReferenceAgent)_agent;
		DefaultAgentState as = (DefaultAgentState)agent.getDataStore();

		if(oldState == Agent.State.WAITING_FOR_HELLO && newState == Agent.State.READY) {
			as.setCreationTime(Instant.now());
			actuator.notifyAgentConnection(as);
		} else if(newState == Agent.State.SHUTDOWN) {
			as.setExpired(true);
			actuator.notifyAgentDisconnection(as.getUUID());
		}

		nimrod.updateAgent(as);
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
