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

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;

import static au.edu.uq.rcc.nimrodg.api.AgentInfo.State;
import static au.edu.uq.rcc.nimrodg.api.AgentInfo.ShutdownReason;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public interface Agent {

	enum ClientState {
		WAITING_FOR_INIT,
		IDLE,
		IN_JOB,
		STOPPED
	}

	/**
	 * Retrieve the current server-side state of the agent.
	 *
	 * @return The current server-side state of the agent.
	 */
	State getState();

	/**
	 * Retrieve the name of the AMQP queue the agent is on.
	 *
	 * @return The name of the AMQP queue the agent is on. If {@link #getState()} returns
	 * {@link State#WAITING_FOR_HELLO}, this value is null.
	 */
	String getQueue();

	/**
	 * Retrieve the UUID of the agent.
	 *
	 * @return The UUID of the agent. If {@link #getState()} returns {@link State#WAITING_FOR_HELLO}, this value is
	 * null.
	 */
	UUID getUUID();

	/**
	 * Get the POSIX signal that caused the agent to shutdown.
	 *
	 * @return The POSIX signal that caused the agent to shutdown. This is only meaningful if {@link #getState()}
	 * returns {@link State#SHUTDOWN} and {@link #getShutdownReason()} returns
	 * {@link ShutdownReason#HostSignal}.
	 */
	int getShutdownSignal();

	/**
	 * Get the reason the agent shutdown.
	 *
	 * @return The reason the agent shutdown.
	 */
	ShutdownReason getShutdownReason();

	/**
	 * Get the instant at which the last message was received.
	 * @return The instant at which the last message was received, or null if not heard from.
	 */
	Instant getLastHeardFrom();

	/**
	 * Submit a job to the agent.
	 *
	 * <ul>
	 * <li>This will invoke the {@link #sendMessage(AgentMessage.Builder)}.</li>
	 * <li> If successful, this will invoke {@link #reportJobSubmit(NetworkJob)}.</li>
	 * </ul>
	 *
	 * @param job The processed job definition.
	 * @throws IOException If sending the message fails. If this is thrown, the state is reset back to
	 * {@link State#READY}.
	 * @throws IllegalArgumentException If {@code job} is null.
	 * @throws IllegalArgumentException If {@code job} does not contain at least one command.
	 * @throws IllegalStateException If {@link #getState()} would not return {@link State#READY}.
	 */
	void submitJob(NetworkJob job) throws IOException, IllegalArgumentException, IllegalStateException;

	/**
	 * Request cancellation of the current job.
	 *
	 * <ul>
	 * <li>This has no effect on the state of the agent. The agent will respond via a message.</li>
	 * </ul>
	 *
	 * @throws IOException If sending the message fails.
	 * @throws IllegalStateException If @{@link #getState()} would not return {@link State#BUSY}.
	 */
	void cancelJob() throws IOException, IllegalStateException;

	/**
	 * Send a heartbeat ping.
	 * @throws IOException If sending the message fails.
	 * @throws IllegalStateException If @{@link #getState()} would return {@link State#SHUTDOWN}.
	 */
	void ping() throws IOException, IllegalStateException;

	/**
	 * Request termination of the agent and cancellation of the current job (if any).
	 *
	 * <ul>
	 * <li>If {@link #getState()} would return {@link State#SHUTDOWN}, this is a no-op.</li>
	 * <li>
	 * If {@link #getState()} would return {@link State#WAITING_FOR_HELLO}, this simulates a termination. The state will
	 * be changed to {@link State#SHUTDOWN} and {@link #getShutdownReason()} will return
	 * {@link ShutdownReason#HostSignal}.
	 * </li>
	 * <li>This may be called at any point in the agent's lifecycle.</li>
	 * </ul>
	 *
	 * @throws IOException If sending the message fails.
	 */
	void terminate() throws IOException;

	/**
	 * Process an incoming message from the agent.
	 *
	 * @param msg The message to process.
	 * @param receivedAt The instant this message was received.
	 * @throws IOException If any response messages fail.
	 * @throws IllegalArgumentException If {@code msg} is null.
	 * @throws IllegalArgumentException If {@code !msg.getAgentUUID().equals(getUUID())}.
	 * @throws IllegalStateException If the type of the message is not valid for the current state.
	 */
	void processMessage(AgentMessage msg, Instant receivedAt) throws IOException, IllegalArgumentException, IllegalStateException;

	/**
	 * Force the state to {@link State#SHUTDOWN}, effectively cutting communication off from the remote client.<ul>
	 * <li>Think very carefully before you do this.</li>
	 * <li>This will cause any incoming messages from the remote client to be dropped.</li>
	 * </ul>
	 *
	 * @param reason The shutdown reason.
	 * @param signal The shutdown signal.
	 */
	void disconnect(ShutdownReason reason, int signal);

	/**
	 * Called when the agent wants to send a message to the remote client.
	 *
	 * @param msg The message to send.
	 * @throws IOException If sending the message fails.
	 */
	void sendMessage(AgentMessage.Builder msg) throws IOException;

	/**
	 * Called after submit message has been sent.
	 *
	 * @param job The submitted job.
	 */
	void reportJobSubmit(NetworkJob job);

	/**
	 * Called when the agent has received a job status update.
	 *
	 * @param au The update message.
	 */
	void reportJobUpdate(AgentUpdate au);

	/**
	 * Called when the agent receives a pong.
	 * @param pong The pong message.
	 */
	void reportPong(AgentPong pong);

	/**
	 * Called upon a state change.
	 *
	 * @param oldState The old state. If this is null, then the initial state of the agent is being set.
	 * @param newState The new state.
	 */
	void reportStateChange(State oldState, State newState);

	static String stateToString(State state) {
		switch(state) {
			case SHUTDOWN:
				return "SHUTDOWN";
			case READY:
				return "READY";
			case BUSY:
				return "BUSY";
			case WAITING_FOR_HELLO:
				return "WAITING_FOR_HELLO";
		}

		throw new IllegalArgumentException();
	}

	static State stateFromString(String s) {
		switch(s) {
			case "SHUTDOWN":
				return State.SHUTDOWN;
			case "READY":
				return State.READY;
			case "BUSY":
				return State.BUSY;
			case "WAITING_FOR_HELLO":
				return State.WAITING_FOR_HELLO;
		}

		throw new IllegalArgumentException();
	}

	static String clientStateToString(ClientState state) {
		switch(state) {
			case WAITING_FOR_INIT:
				return "WAITING_FOR_INIT";
			case IDLE:
				return "IDLE";
			case IN_JOB:
				return "IN_JOB";
			case STOPPED:
				return "STOPPED";
		}

		throw new IllegalArgumentException();
	}

	static ClientState clientStateFromString(String s) {
		switch(s) {
			case "WAITING_FOR_INIT":
				return ClientState.WAITING_FOR_INIT;
			case "IDLE":
				return ClientState.IDLE;
			case "IN_JOB":
				return ClientState.IN_JOB;
			case "STOPPED":
				return ClientState.STOPPED;
		}
		throw new IllegalArgumentException();
	}
}
