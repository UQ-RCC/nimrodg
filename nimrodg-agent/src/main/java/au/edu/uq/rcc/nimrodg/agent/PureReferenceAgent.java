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
package au.edu.uq.rcc.nimrodg.agent;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentInit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPing;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown.Reason;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * A reference implementation of the agent logic.
 *
 * This is the golden version. I WORK DON'T TOUCH ME. Keeping this around for reference.
 */
public class PureReferenceAgent implements Agent {

	public interface AgentListener {

		void send(Agent agent, AgentMessage msg) throws IOException;

		void onStateChange(Agent agent, State oldState, State newState);

		void onJobSubmit(Agent agent, AgentSubmit as);

		void onJobUpdate(Agent agent, AgentUpdate au);

		void onPong(Agent agent, AgentPong pong);
	}

	private final AgentListener listener;
	private State state;
	private String queue;
	private UUID uuid;
	private int shutdownSignal;
	private Reason reason;
	private Instant lastHeardFrom;

	public PureReferenceAgent(AgentListener listener) {
		if((this.listener = listener) == null) {
			throw new IllegalArgumentException("sender cannot be null");
		}

		this.state = null;
		reset();
	}

	@Override
	public State getState() {
		return state;
	}

	@Override
	public String getQueue() {
		return queue;
	}

	@Override
	public UUID getUUID() {
		return uuid;
	}

	@Override
	public int getShutdownSignal() {
		return shutdownSignal;
	}

	@Override
	public AgentShutdown.Reason getShutdownReason() {
		return reason;
	}

	@Override
	public Instant getLastHeardFrom() {
		return lastHeardFrom;
	}

	private void setState(State state) {
		State old = state;

		this.state = state;

		reportStateChange(old, state);
	}

	@Override
	public void disconnect(Reason reason, int signal) {
		if(this.getState() != State.SHUTDOWN) {
			this.reason = reason;
			this.shutdownSignal = signal;
			setState(State.SHUTDOWN);
		}
	}

	public final void reset() {
		if(state != State.SHUTDOWN && state != null) {
			throw new IllegalStateException("Cannot reset, disconnect agent first.");
		}

		queue = null;
		uuid = null;
		shutdownSignal = -1;
		reason = Reason.HostSignal;
		lastHeardFrom = null;
		setState(State.WAITING_FOR_HELLO);
	}

	@Override
	public void submitJob(NetworkJob job) throws IOException {
		if(state != State.READY) {
			throw new IllegalStateException("Cannot submit, wait for exiting job to finish.");
		}

		/* Do some sanity checks on the job */
		if(job.numCommands <= 0) {
			throw new IllegalArgumentException("Jobs must have at least one command.");
		}

		setState(State.BUSY);
		AgentSubmit as = new AgentSubmit(uuid, job);
		try {
			sendMessage(as);
		} catch(IOException e) {
			setState(State.READY);
			throw e;
		}
		reportJobSubmit(as);

	}

	@Override
	public void cancelJob() throws IOException {
		if(state != State.BUSY) {
			throw new IllegalStateException("Can't cancel a nonexistent job");
		}

		sendMessage(new AgentLifeControl(uuid, AgentLifeControl.Operation.Cancel));
	}

	@Override
	public void ping() throws IOException {
		if(state == State.SHUTDOWN) {
			throw new IllegalStateException("Can't ping a stopped agent");
		}
		sendMessage(new AgentPing(uuid));
	}

	@Override
	public void terminate() throws IOException {
		if(state == State.SHUTDOWN) {
			return;
		}

		if(state == State.WAITING_FOR_HELLO) {
			/* Fake a shutdown */
			shutdownSignal = -1;
			reason = Reason.Requested;
			return;
		}

		sendMessage(new AgentLifeControl(uuid, AgentLifeControl.Operation.Terminate));
	}

	@Override
	public void processMessage(AgentMessage msg, Instant receivedAt) throws IOException {
		if(uuid != null) {
			if(!msg.getAgentUUID().equals(uuid)) {
				throw new IllegalArgumentException("message UUID mismatch");
			}
		}

		if(state == State.SHUTDOWN) {
			throw new IllegalStateException("Agent is dead, cannot process messages.");
		} else if(state == State.WAITING_FOR_HELLO) {
			if(msg.getType() != AgentMessage.Type.Hello) {
				throw new IllegalStateException(String.format("%s not valid for state %s", AgentMessage.getTypeString(msg.getType()), state));
			}
			lastHeardFrom = receivedAt;

			AgentHello hello = (AgentHello)msg;
			uuid = hello.getAgentUUID();
			queue = hello.queue;

			sendMessage(new AgentInit(msg.getAgentUUID()));
			setState(State.READY);
		} else if(state == State.READY) {
			if(msg.getType() == AgentMessage.Type.Pong) {
				lastHeardFrom = receivedAt;
				reportPong((AgentPong)msg);
			} else if(msg.getType() == AgentMessage.Type.Shutdown) {
				lastHeardFrom = receivedAt;

				AgentShutdown shutdown = (AgentShutdown)msg;
				shutdownSignal = shutdown.signal;
				reason = shutdown.reason;
				setState(State.SHUTDOWN);
			} else {
				throw new IllegalStateException(String.format("%s not valid for state %s", AgentMessage.getTypeString(msg.getType()), state));
			}
		} else if(state == State.BUSY) {
			if(msg.getType() == AgentMessage.Type.Pong) {
				lastHeardFrom = receivedAt;
				reportPong((AgentPong)msg);
			} else if(msg.getType() == AgentMessage.Type.Update) {
				lastHeardFrom = receivedAt;

				AgentUpdate au = (AgentUpdate)msg;
				AgentUpdate.CommandResult_ res = au.getCommandResult();
				if(au.getAction() == AgentUpdate.Action.Stop) {
					setState(State.READY);
				}
				reportJobUpdate(au);
			} else if(msg.getType() == AgentMessage.Type.Shutdown) {
				lastHeardFrom = receivedAt;

				AgentShutdown shutdown = (AgentShutdown)msg;
				shutdownSignal = shutdown.signal;
				reason = shutdown.reason;
				setState(State.SHUTDOWN);
			} else {
				throw new IllegalStateException(String.format("%s not valid for state %s", AgentMessage.getTypeString(msg.getType()), state));
			}

		}
	}

	@Override
	public void sendMessage(AgentMessage msg) throws IOException {
		listener.send(this, msg);
	}

	@Override
	public void reportJobSubmit(AgentSubmit as) {
		listener.onJobSubmit(this, as);
	}

	@Override
	public void reportJobUpdate(AgentUpdate au) {
		listener.onJobUpdate(this, au);
	}

	@Override
	public void reportStateChange(State oldState, State newState) {
		listener.onStateChange(this, oldState, newState);
	}

	@Override
	public void reportPong(AgentPong pong) {
		listener.onPong(this, pong);
	}
}
