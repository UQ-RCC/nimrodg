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
package au.edu.uq.rcc.nimrodg.debug.agent;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.agent.messages.json.JsonBackend;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.master.AMQPMessage;
import au.edu.uq.rcc.nimrodg.master.AMQProcessorImpl;
import au.edu.uq.rcc.nimrodg.master.MessageQueueListener;
import au.edu.uq.rcc.nimrodg.master.sig.SigUtils;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import javax.swing.*;

public class Controller {

	private final AgentGUI view;

	private AMQProcessorImpl amqp;

	private ReferenceAgent agent;
	private final _AgentListener agentListener;
	private final ILogger logger;
	private final AgentStatusPanel statusPanel;

	private final MessageWindow messageWindow;

	private final ArrayList<Message> messages;

	private static final String SAMPLE_PLANFILE = "parameter x int range from 0 to 10\n"
			+ "parameter y int range from 0 to 10\n"
			+ "\n"
			+ "task main\n"
			+ "    shexec echo \"x = ${x}\" \"y = ${y}\"\n"
			+ "endtask";

	private static final String SAMPLE_RUNFILE = "variable x index 0 list 0 1\n"
			+ "variable y index 1 list 0 1\n"
			+ "\n"
			+ "jobs\n"
			+ "    0001 0 0\n"
			+ "    0002 1 0\n"
			+ "endjobs\n"
			+ "task main\n"
			+ "    shexec echo \"x = ${x}\" \"y = ${y}\"\n"
			+ "endtask";

	public Controller() {
		view = new AgentGUI(new _ActionListener());
		view.addWindowListener(new _WindowListener());

		SwingUtilities.invokeLater(() -> view.setVisible(true));

		view.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
		view.setConnectStatus("Disconnected", Color.red);
		view.getAgentPanel().addListener(new _AgentPanelListener());

		logger = view.getLogger();
		statusPanel = view.getStatusPanel();

		amqp = null;

		agent = null;
		agentListener = new _AgentListener();

		agent = new ReferenceAgent(new DefaultAgentState(), agentListener);
		messageWindow = new MessageWindow();
		messages = new ArrayList<>();
		messageWindow.getMessagePanel().setMessages(messages);
	}

	public boolean isConnected() {
		return amqp != null;
	}

	public void connect(String uri) {
		if(isConnected()) {
			throw new IllegalStateException();
		}

		String routingKey = view.getRoutingKey();
		view.setConnectionPanelState(AgentGUI.ConnectionPanelState.LOCKED);
		view.setConnectStatus("Connecting...", Color.black);
		view.setConnectProgress(0.0f);

		SwingUtilities.invokeLater(() -> {
			try {
				amqp = new AMQProcessorImpl(new URI(uri), new Certificate[0], "TLSv1.2", view.getRoutingKey(), true, true, new _MessageQueueListener(), ForkJoinPool.commonPool(), view.getSigningAlgorithm());
				logger.log(ILogger.Level.INFO, "Connected to %s", uri);

				view.setConnectProgress(1.0f);
				view.setConnectStatus(String.format("%s <===> %s (via %s)", amqp.getQueue(), amqp.getExchange(), routingKey), Color.black);
				view.setConnectionPanelState(AgentGUI.ConnectionPanelState.DISCONNECT);
			} catch(IOException | URISyntaxException | GeneralSecurityException | TimeoutException e) {
				amqp = null;
				view.setConnectProgress(0.0f);
				view.setConnectStatus("Disconnected", Color.red);
				view.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
				logger.log(ILogger.Level.ERR, e);
			}
		});
	}

	public void disconnect() {
		if(!isConnected()) {
			throw new IllegalStateException();
		}

		try {
			amqp.close();
		} catch(IOException e) {
			logger.log(ILogger.Level.ERR, e);
		}

		amqp = null;
		view.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
		view.setConnectStatus("Disconnected", Color.red);
		view.setConnectProgress(0.0f);
	}

	private void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if(cmd.equals("connect")) {
			if(isConnected()) {
				disconnect();
			} else {
				connect(view.getAMQPUri());
			}
		} else if(cmd.equals("file->exit")) {
			view.dispatchEvent(new WindowEvent(view, WindowEvent.WINDOW_CLOSING));
		} else if(cmd.equals("file->clear_log")) {
			logger.clear();
		} else if(cmd.equals("view->show_message_window")) {
			messageWindow.setVisible(view.getShowMessageWindow());
		}
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch(Exception e) {

		}
		new Controller();
	}

	private class _ActionListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			Controller.this.actionPerformed(e);
		}
	}

	private class _WindowListener extends WindowAdapter {

		@Override
		public void windowClosed(WindowEvent e) {
			if(isConnected()) {
				disconnect();
			}
			messageWindow.dispose();
		}
	}

	private MessageQueueListener.MessageOperation handleAgentMessage(long tag, AMQPMessage amsg) throws IOException {
		messages.add(Message.create(amsg, true));
		messageWindow.getMessagePanel().refreshMessages();

		AgentMessage msg = amsg.message;
		if(msg == null) {
			return MessageQueueListener.MessageOperation.Reject;
		}

		boolean validSig;

		try {
			validSig = SigUtils.verifySignature(amsg.authHeader, view.getSecretKey(), amsg.basicProperties, amsg.body);
		} catch(NoSuchAlgorithmException e) {
			logger.log(ILogger.Level.ERR, "Error signing header using %s", amsg.authHeader.algorithm);
			logger.log(ILogger.Level.ERR, e);
			return MessageQueueListener.MessageOperation.Reject;
		}

		if(!validSig) {
			return MessageQueueListener.MessageOperation.Reject;
		}

		if(!SigUtils.validateMessage(amsg.basicProperties, amsg.authHeader, amsg.message, SigUtils.DEFAULT_APPID)) {
			return MessageQueueListener.MessageOperation.Reject;
		}

		UUID uuid = msg.getAgentUUID();

		boolean terminate =
				(agent.getUUID() != null && !uuid.equals(agent.getUUID())) ||	/* Mismatching UUID. */
						(agent.getState() == AgentInfo.State.SHUTDOWN);				/* Already shutdown. */

		if(terminate) {
			if(msg.getType() == AgentMessage.Type.Hello) {
				AgentHello hello = (AgentHello)msg;
				logger.log(ILogger.Level.WARN, "Received agent.hello with key '%s', sending termination...", hello.queue);
				sendMessage(hello.queue, new AgentLifeControl.Builder()
						.agentUuid(uuid)
						.operation(AgentLifeControl.Operation.Terminate));
			} else {
				logger.log(ILogger.Level.WARN, "Ignoring message from unknown agent %s", uuid);
			}
			return MessageQueueListener.MessageOperation.Ack;
		}

		if(agent.getState() == null) {
			agent.reset(msg.getAgentUUID());
		}

		try {
			agent.processMessage(msg, Instant.now());
			return MessageQueueListener.MessageOperation.Ack;
		} catch(IllegalStateException e) {
			logger.log(ILogger.Level.ERR, e);
			return MessageQueueListener.MessageOperation.Reject;
		}
	}

	private void sendMessage(String key, AgentMessage.Builder<?> msg) throws IOException {
		AMQPMessage amsg = amqp.sendMessage(
				key,
				SigUtils.buildAccessKey(agent.getUUID()),
				view.getSecretKey(),
				msg.timestamp(Instant.now()).build()
		);
		messages.add(Message.create(amsg, false));
		messageWindow.getMessagePanel().refreshMessages();
	}

	private void onAgentStateChange(Agent agent, AgentInfo.State oldState, AgentInfo.State newState) {
		logger.log(ILogger.Level.INFO, "State Change: %s => %s", oldState, newState);

		if(newState == AgentInfo.State.SHUTDOWN) {
			if(agent.getShutdownReason() == AgentInfo.ShutdownReason.Requested) {
				logger.log(ILogger.Level.INFO, "Agent terminated by request");
			} else if(agent.getShutdownReason() == AgentInfo.ShutdownReason.HostSignal) {
				logger.log(ILogger.Level.INFO, "Agent terminated by host signal %d", agent.getShutdownSignal());
			}
		}

		view.getAgentPanel().setState(newState);
		statusPanel.update(agent);
	}

	private void onAgentJobSubmit(Agent agent, NetworkJob job) {
		statusPanel.setJob(job);
	}

	private void onAgentJobUpdate(Agent agent, AgentUpdate au) {
		statusPanel.updateCommand(agent, au);
	}

	private void onAgentPong(Agent agent, AgentPong pong) {
		statusPanel.update(agent);
	}

	private void onPanelSubmit(String jobText) {
		CompiledRun run;
		try {
			run = ANTLR4ParseAPIImpl.INSTANCE.parseRunToBuilder(jobText).build();
		} catch(RunfileBuildException | NullPointerException e) {
			logger.log(ILogger.Level.ERR, e);
			return;
		} catch(PlanfileParseException ex) {
			ex.getErrors().forEach(e -> logger.log(ILogger.Level.ERR, e.toString()));
			return;
		}

		logger.log(ILogger.Level.INFO, "Compiled runfile, %d variables, %d jobs, %d tasks.", run.numVariables, run.numJobs, run.numTasks);

		if(run.numJobs != 1) {
			logger.log(ILogger.Level.ERR, "Planfile cannot have > 1 jobs.");
			return;
		}

		NetworkJob j = MsgUtils.resolveJob(
				UUID.randomUUID(),
				run,
				1,
				Task.Name.Main,
				view.getTransferURI(),
				"expname"
		);
		try {
			agent.submitJob(j);
		} catch(Exception e) {
			logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelPing() {
		try {
			agent.ping();
		} catch(IOException e) {
			logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelCancel() {
		try {
			agent.cancelJob();
		} catch(IOException e) {
			logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelTerminate() {
		try {
			agent.terminate();
		} catch(IOException e) {
			logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelReset() {
		agent.disconnect(AgentInfo.ShutdownReason.Requested, -1);
		agent.reset(null);
		statusPanel.setJob(null);
		messages.clear();
		messageWindow.getMessagePanel().refreshMessages();
	}

	private class _AgentListener implements ReferenceAgent.AgentListener {

		@Override
		public void send(Agent agent, AgentMessage.Builder<?> msg) throws IOException {
			Controller.this.sendMessage(agent.getQueue(), msg);
		}

		@Override
		public void onStateChange(Agent agent, AgentInfo.State oldState, AgentInfo.State newState) {
			Controller.this.onAgentStateChange(agent, oldState, newState);
		}

		@Override
		public void onJobUpdate(Agent agent, AgentUpdate au) {
			Controller.this.onAgentJobUpdate(agent, au);
		}

		@Override
		public void onJobSubmit(Agent agent, NetworkJob job) {
			Controller.this.onAgentJobSubmit(agent, job);
		}

		@Override
		public void onPong(Agent agent, AgentPong pong) {
			Controller.this.onAgentPong(agent, pong);
		}
	}

	private class _AgentPanelListener implements AgentControlPanel.Listener {

		@Override
		public void onSubmit(String jobText) {
			Controller.this.onPanelSubmit(jobText);
		}

		@Override
		public void onPing() {
			Controller.this.onPanelPing();
		}

		@Override
		public void onCancel() {
			Controller.this.onPanelCancel();
		}

		@Override
		public void onTerminate() {
			Controller.this.onPanelTerminate();
		}

		@Override
		public void onReset() {
			Controller.this.onPanelReset();
		}

	}

	private class _MessageQueueListener implements MessageQueueListener {

		@Override
		public Optional<MessageOperation> processAgentMessage(long tag, AMQPMessage msg) throws IllegalStateException, IOException {
			return Optional.of(handleAgentMessage(tag, msg));
		}
	}
}
