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
 * This is identical to {@link PureReferenceAgent} except it has a swappable storage backend.
 */
public class ReferenceAgent implements Agent {

	public interface AgentListener {

		void send(Agent agent, AgentMessage msg) throws IOException;

		void onStateChange(Agent agent, State oldState, State newState);

		void onJobSubmit(Agent agent, AgentSubmit as);

		void onJobUpdate(Agent agent, AgentUpdate au);

		void onPong(Agent agent, AgentPong pong);
	}

	private final AgentListener listener;
	private final AgentState storage;

	/**
	 * Create a new agent, setting the data store to the initial values.
	 *
	 * @param dataStore The data storage instance. This will be initialised to default values.
	 * @param listener The listener.
	 */
	public ReferenceAgent(AgentState dataStore, AgentListener listener) {
		this(dataStore, listener, true);
	}

	/**
	 * Create a new agent with the state currently in the given data store.
	 *
	 * @param dataStore The data storage instance. It is the users responsibility that this is consistent.
	 * @param listener The listener.
	 * @param initial If set, initialise the data store to default values.
	 */
	public ReferenceAgent(AgentState dataStore, AgentListener listener, boolean initial) {
		if((storage = dataStore) == null) {
			throw new IllegalArgumentException("dataStore cannot be null");
		}

		if((this.listener = listener) == null) {
			throw new IllegalArgumentException("sender cannot be null");
		}

		if(initial) {
			storage.setState(null);
			reset();
		}
	}

	@Override
	public State getState() {
		return storage.getState();
	}

	@Override
	public String getQueue() {
		return storage.getQueue();
	}

	@Override
	public UUID getUUID() {
		return storage.getUUID();
	}

	@Override
	public int getShutdownSignal() {
		return storage.getShutdownSignal();
	}

	@Override
	public AgentShutdown.Reason getShutdownReason() {
		return storage.getShutdownReason();
	}

	@Override
	public Instant getLastHeardFrom() {
		return storage.getLastHeardFrom();
	}

	private void setState(State state) {
		State old = storage.getState();
		storage.setState(state);
		reportStateChange(old, state);
	}

	@Override
	public void disconnect(Reason reason, int signal) {
		if(this.getState() != State.SHUTDOWN) {
			storage.setShutdownReason(reason);
			storage.setShutdownSignal(signal);
			setState(State.SHUTDOWN);
		}
	}

	public final void reset() {
		State state = storage.getState();
		if(state != State.SHUTDOWN && state != null) {
			throw new IllegalStateException("Cannot reset, disconnect agent first.");
		}

		storage.setQueue(null);
		storage.setUUID(null);
		storage.setShutdownSignal(-1);
		storage.setShutdownReason(Reason.HostSignal);
		setState(State.WAITING_FOR_HELLO);
	}

	@Override
	public void submitJob(NetworkJob job) throws IOException {
		if(storage.getState() != State.READY) {
			throw new IllegalStateException("Cannot submit, wait for exiting job to finish.");
		}

		/* Do some sanity checks on the job */
		if(job.numCommands <= 0) {
			throw new IllegalArgumentException("Jobs must have at least one command.");
		}

		setState(State.BUSY);
		AgentSubmit as = new AgentSubmit(storage.getUUID(), job);
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
		if(storage.getState() != State.BUSY) {
			throw new IllegalStateException("Can't cancel a nonexistent job");
		}

		sendMessage(new AgentLifeControl(storage.getUUID(), AgentLifeControl.Operation.Cancel));
	}

	@Override
	public void ping() throws IOException {
		if(storage.getState() == State.SHUTDOWN) {
			throw new IllegalStateException("Can't ping a stopped agent");
		}
		sendMessage(new AgentPing(storage.getUUID()));
	}

	@Override
	public void terminate() throws IOException {
		if(storage.getState() == State.SHUTDOWN) {
			return;
		}

		if(storage.getState() == State.WAITING_FOR_HELLO) {
			/* Fake a shutdown */
			storage.setShutdownSignal(-1);
			storage.setShutdownReason(Reason.Requested);
			return;
		}

		sendMessage(new AgentLifeControl(storage.getUUID(), AgentLifeControl.Operation.Terminate));
	}

	@Override
	public void processMessage(AgentMessage msg, Instant receivedAt) throws IOException {
		UUID uuid = storage.getUUID();
		if(uuid != null) {
			if(!msg.getAgentUUID().equals(uuid)) {
				throw new IllegalArgumentException("message UUID mismatch");
			}
		}

		final State state = storage.getState();

		if(state == State.SHUTDOWN) {
			throw new IllegalStateException("Agent is dead, cannot process messages.");
		} else if(state == State.WAITING_FOR_HELLO) {
			if(msg.getType() != AgentMessage.Type.Hello) {
				throw new IllegalStateException(String.format("%s not valid for state %s", AgentMessage.getTypeString(msg.getType()), state));
			}
			storage.setLastHeardFrom(receivedAt);

			AgentHello hello = (AgentHello)msg;
			storage.setUUID(hello.getAgentUUID());
			storage.setQueue(hello.queue);

			sendMessage(new AgentInit(msg.getAgentUUID()));
			setState(State.READY);
		} else if(state == State.READY) {
			if(msg.getType() == AgentMessage.Type.Pong) {
				storage.setLastHeardFrom(receivedAt);
				reportPong((AgentPong)msg);
			} else if(msg.getType() == AgentMessage.Type.Shutdown) {
				storage.setLastHeardFrom(receivedAt);

				AgentShutdown shutdown = (AgentShutdown)msg;
				storage.setShutdownSignal(shutdown.signal);
				storage.setShutdownReason(shutdown.reason);
				setState(State.SHUTDOWN);
			} else {
				throw new IllegalStateException(String.format("%s not valid for state %s", AgentMessage.getTypeString(msg.getType()), state));
			}
		} else if(state == State.BUSY) {
			if(msg.getType() == AgentMessage.Type.Pong) {
				storage.setLastHeardFrom(receivedAt);
				reportPong((AgentPong)msg);
			} else if(msg.getType() == AgentMessage.Type.Update) {
				storage.setLastHeardFrom(receivedAt);

				AgentUpdate au = (AgentUpdate)msg;
				AgentUpdate.CommandResult_ res = au.getCommandResult();
				if(au.getAction() == AgentUpdate.Action.Stop) {
					setState(State.READY);
				}
				reportJobUpdate(au);
			} else if(msg.getType() == AgentMessage.Type.Shutdown) {
				storage.setLastHeardFrom(receivedAt);

				AgentShutdown shutdown = (AgentShutdown)msg;
				storage.setShutdownSignal(shutdown.signal);
				storage.setShutdownReason(shutdown.reason);
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

	public AgentState getDataStore() {
		return storage;
	}
}
