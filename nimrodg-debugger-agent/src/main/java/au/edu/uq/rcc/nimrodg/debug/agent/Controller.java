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
import au.edu.uq.rcc.nimrodg.agent.MessageBackend;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.master.AMQPMessage;
import au.edu.uq.rcc.nimrodg.master.AMQProcessorImpl;
import au.edu.uq.rcc.nimrodg.master.MessageQueueListener;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.swing.SwingUtilities;

public class Controller {

	private final AgentGUI m_View;

	private AMQProcessorImpl m_AMQP;

	private ReferenceAgent m_Agent;
	private final _AgentListener m_AgentListener;
	private final ILogger m_Logger;
	private final AgentStatusPanel m_StatusPanel;

	private final MessageWindow m_MessageWindow;

	private final ArrayList<Message> m_Messages;

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
		m_View = new AgentGUI(new _ActionListener());
		m_View.addWindowListener(new _WindowListener());

		SwingUtilities.invokeLater(() -> m_View.setVisible(true));

		m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
		m_View.setConnectStatus("Disconnected", Color.red);
		m_View.getAgentPanel().addListener(new _AgentPanelListener());

		m_Logger = m_View.getLogger();
		m_StatusPanel = m_View.getStatusPanel();

		m_AMQP = null;

		m_Agent = null;
		m_AgentListener = new _AgentListener();

		m_Agent = new ReferenceAgent(new DefaultAgentState(), m_AgentListener);
		m_StatusPanel.setAgent(m_Agent);
		m_MessageWindow = new MessageWindow();
		m_Messages = new ArrayList<>();
		m_MessageWindow.getMessagePanel().setMessages(m_Messages);
	}

	public boolean isConnected() {
		return m_AMQP != null;
	}

	public void connect(String uri) {
		if(isConnected()) {
			throw new IllegalStateException();
		}

		String routingKey = m_View.getRoutingKey();
		m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.LOCKED);
		m_View.setConnectStatus("Connecting...", Color.black);
		m_View.setConnectProgress(0.0f);

		SwingUtilities.invokeLater(() -> {
			try {
				m_AMQP = new AMQProcessorImpl(new URI(uri), new Certificate[0], "TLSv1.2", m_View.getRoutingKey(), true, true, new _MessageQueueListener(), ForkJoinPool.commonPool());
				m_Logger.log(ILogger.Level.INFO, "Connected to %s", uri);

				m_View.setConnectProgress(1.0f);
				m_View.setConnectStatus(String.format("%s <===> %s (via %s)", m_AMQP.getQueue(), m_AMQP.getExchange(), routingKey), Color.black);
				m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.DISCONNECT);
			} catch(IOException | URISyntaxException | GeneralSecurityException | TimeoutException e) {
				m_AMQP = null;
				m_View.setConnectProgress(0.0f);
				m_View.setConnectStatus("Disconnected", Color.red);
				m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
				m_Logger.log(ILogger.Level.ERR, e);
			}
		});
	}

	public void disconnect() {
		if(!isConnected()) {
			throw new IllegalStateException();
		}

		try {
			m_AMQP.close();
		} catch(IOException e) {
			m_Logger.log(ILogger.Level.ERR, e);
		}

		m_AMQP = null;
		m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);
		m_View.setConnectStatus("Disconnected", Color.red);
		m_View.setConnectProgress(0.0f);
	}

	private void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if(cmd.equals("connect")) {
			if(isConnected()) {
				disconnect();
			} else {
				connect(m_View.getAMQPUri());
			}
		} else if(cmd.equals("file->exit")) {
			m_View.dispatchEvent(new WindowEvent(m_View, WindowEvent.WINDOW_CLOSING));
		} else if(cmd.equals("file->clear_log")) {
			m_Logger.clear();
		} else if(cmd.equals("view->show_message_window")) {
			m_MessageWindow.setVisible(m_View.getShowMessageWindow());
		}
	}

	public static void main(String[] args) {
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
			m_MessageWindow.dispose();
		}
	}

	private MessageQueueListener.MessageOperation handleAgentMessage(long tag, AMQPMessage amsg) throws IOException {
		m_Messages.add(Message.create(amsg, true));
		m_MessageWindow.getMessagePanel().refreshMessages();

		AgentMessage msg = amsg.message;
		if(msg == null) {
			return MessageQueueListener.MessageOperation.Reject;
		}

		UUID uuid = msg.getAgentUUID();

		boolean terminate =
				(m_Agent.getUUID() != null && !uuid.equals(m_Agent.getUUID())) ||	/* Mismatching UUID. */
						(m_Agent.getState() == Agent.State.SHUTDOWN);				/* Already shutdown. */

		if(terminate) {
			if(msg.getType() == AgentMessage.Type.Hello) {
				AgentHello hello = (AgentHello)msg;
				m_Logger.log(ILogger.Level.WARN, "Received agent.hello with key '%s', sending termination...", hello.queue);
				sendMessage(hello.queue, new AgentLifeControl(uuid, AgentLifeControl.Operation.Terminate));
			} else {
				m_Logger.log(ILogger.Level.WARN, "Ignoring message from unknown agent %s", uuid);
			}
			return MessageQueueListener.MessageOperation.Ack;
		}

		if(m_Agent.getState() == null) {
			m_Agent.reset(msg.getAgentUUID());
		}

		try {
			m_Agent.processMessage(msg, Instant.now());
			return MessageQueueListener.MessageOperation.Ack;
		} catch(IllegalStateException e) {
			m_Logger.log(ILogger.Level.ERR, e);
			return MessageQueueListener.MessageOperation.Reject;
		}
	}

	private void sendMessage(String key, AgentMessage msg) throws IOException {
		AMQPMessage amsg = m_AMQP.sendMessage(key, msg);
		m_Messages.add(Message.create(amsg, false));
		m_MessageWindow.getMessagePanel().refreshMessages();
	}

	private void onAgentStateChange(Agent agent, Agent.State oldState, Agent.State newState) {
		m_Logger.log(ILogger.Level.INFO, "State Change: %s => %s", oldState, newState);

		if(newState == Agent.State.SHUTDOWN) {
			if(agent.getShutdownReason() == AgentShutdown.Reason.Requested) {
				m_Logger.log(ILogger.Level.INFO, "Agent terminated by request");
			} else if(agent.getShutdownReason() == AgentShutdown.Reason.HostSignal) {
				m_Logger.log(ILogger.Level.INFO, "Agent terminated by host signal %d", agent.getShutdownSignal());
			}
		}

		m_View.getAgentPanel().setState(newState);
		m_StatusPanel.update();
	}

	private void onAgentJobSubmit(Agent agent, AgentSubmit as) {
		m_StatusPanel.setJob(as.getJob());
	}

	private void onAgentJobUpdate(Agent agent, AgentUpdate au) {
		m_StatusPanel.updateCommand(au);
	}

	private void onAgentPong(Agent agent, AgentPong pong) {
		m_StatusPanel.update();
	}

	private void onPanelSubmit(String jobText) {
		CompiledRun run;
		try {
			run = ANTLR4ParseAPIImpl.INSTANCE.parseRunToBuilder(jobText).build();
		} catch(RunfileBuildException | NullPointerException e) {
			m_Logger.log(ILogger.Level.ERR, e);
			return;
		} catch(PlanfileParseException ex) {
			ex.getErrors().forEach(e -> m_Logger.log(ILogger.Level.ERR, e.toString()));
			return;
		}

		m_Logger.log(ILogger.Level.INFO, "Compiled runfile, %d variables, %d jobs, %d tasks.", run.numVariables, run.numJobs, run.numTasks);

		if(run.numJobs != 1) {
			m_Logger.log(ILogger.Level.ERR, "Planfile cannot have > 1 jobs.");
			return;
		}

		NetworkJob j = MsgUtils.resolveJob(
				UUID.randomUUID(),
				run,
				1,
				Task.Name.Main,
				m_View.getTransferURI(),
				m_View.getAuthToken(),
				"expname"
		);
		try {
			m_Agent.submitJob(j);
		} catch(Exception e) {
			m_Logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelPing() {
		try {
			m_Agent.ping();
		} catch(IOException e) {
			m_Logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelCancel() {
		try {
			m_Agent.cancelJob();
		} catch(IOException e) {
			m_Logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelTerminate() {
		try {
			m_Agent.terminate();
		} catch(IOException e) {
			m_Logger.log(ILogger.Level.ERR, e);
		}
	}

	private void onPanelReset() {
		m_Agent.disconnect(AgentShutdown.Reason.Requested, -1);
		m_Agent.reset(null);
		m_StatusPanel.setJob(null);
		m_Messages.clear();
		m_MessageWindow.getMessagePanel().refreshMessages();
	}

	private class _AgentListener implements ReferenceAgent.AgentListener {

		@Override
		public void send(Agent agent, AgentMessage msg) throws IOException {
			Controller.this.sendMessage(agent.getQueue(), msg);
		}

		@Override
		public void onStateChange(Agent agent, Agent.State oldState, Agent.State newState) {
			Controller.this.onAgentStateChange(agent, oldState, newState);
		}

		@Override
		public void onJobUpdate(Agent agent, AgentUpdate au) {
			Controller.this.onAgentJobUpdate(agent, au);
		}

		@Override
		public void onJobSubmit(Agent agent, AgentSubmit as) {
			Controller.this.onAgentJobSubmit(agent, as);
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
