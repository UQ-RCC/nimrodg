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
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.impl.ForgivingExceptionHandler;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Controller {

	private final AgentGUI m_View;

	private ConnectionFactory m_ConnectionFactory;
	private Connection m_Connection;
	private Channel m_Channel;
	private AMQP.Exchange.DeclareOk m_BroadcastExchangeOk;
	private AMQP.Exchange.DeclareOk m_DirectExchangeOk;
	private AMQP.Queue.DeclareOk m_QueueOk;

	private static final String BROADCAST_EXCHANGE = "amq.fanout";
	private static final String DIRECT_EXCHANGE = "amq.direct";

	private ReferenceAgent m_Agent;
	private final _AgentListener m_AgentListener;
	private final ILogger m_Logger;
	private final AgentStatusPanel m_StatusPanel;

	private final MessageBackend m_MessageBackend;

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

		m_ConnectionFactory = null;
		m_Connection = null;
		m_Channel = null;
		m_Agent = null;
		m_AgentListener = new _AgentListener();

		m_MessageBackend = MessageBackend.createBackend();
	}

	public boolean isConnected() {
		return m_Connection != null;
	}

	public void connect(String uri) {
		if(isConnected()) {
			throw new IllegalStateException();
		}

		m_ConnectionFactory = new ConnectionFactory();
		m_ConnectionFactory.setAutomaticRecoveryEnabled(true);
		m_ConnectionFactory.setTopologyRecoveryEnabled(true);
		m_ConnectionFactory.setExceptionHandler(new ForgivingExceptionHandler() {
			@Override
			protected void log(String message, Throwable e) {
				super.log(message, e);
				m_Logger.log(ILogger.Level.ERR, message);
				m_Logger.log(ILogger.Level.ERR, e);
			}

		});
		try {
			m_ConnectionFactory.setUri(uri);
		} catch(KeyManagementException | NoSuchAlgorithmException | URISyntaxException e) {
			m_ConnectionFactory = null;
			m_View.setConnectStatus(e.getMessage(), Color.red);
			return;
		} catch(NullPointerException e) {
			m_ConnectionFactory = null;
			m_Logger.log(ILogger.Level.ERR, "Connection Error: Invalid AMQP URI");
			return;
		}

		m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.LOCKED);
		m_View.setConnectStatus("Connecting...", Color.black);

		m_View.setConnectProgress(0.0f);

		String routingKey = m_View.getRoutingKey();
		SwingUtilities.invokeLater(() -> {
			try {
				m_View.setConnectStatus("Establishing connection...", Color.black);
				m_Connection = m_ConnectionFactory.newConnection();

				m_Logger.log(ILogger.Level.INFO, "Connected to %s", uri);

				m_View.setConnectProgress(0.2f);
				m_View.setConnectStatus("Creating channel...", Color.black);
				m_Channel = m_Connection.createChannel();
				m_Channel.addConfirmListener(new _ConfirmListener());
				m_Channel.addReturnListener(new _ReturnListener());

				m_View.setConnectProgress(0.4f);
				m_BroadcastExchangeOk = m_Channel.exchangeDeclare(BROADCAST_EXCHANGE, BuiltinExchangeType.FANOUT, true, false, false, null);
				m_DirectExchangeOk = m_Channel.exchangeDeclare(DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, true, false, false, null);
				m_View.setConnectProgress(0.6f);
				m_QueueOk = m_Channel.queueDeclare("", true, true, true, null);
				m_Logger.log(ILogger.Level.INFO, "Created queue %s", m_QueueOk.getQueue());

				m_Channel.queueBind(m_QueueOk.getQueue(), BROADCAST_EXCHANGE, "");
				m_Logger.log(ILogger.Level.INFO, "Bound to broadcast exchange %s", BROADCAST_EXCHANGE);

				m_Channel.queueBind(m_QueueOk.getQueue(), DIRECT_EXCHANGE, routingKey);
				m_Logger.log(ILogger.Level.INFO, "Bound to direct exchange %s with routing key %s", DIRECT_EXCHANGE, routingKey);

				m_Channel.basicConsume(m_QueueOk.getQueue(), false, new _Consumer(m_Channel));
				m_View.setConnectProgress(0.8f);
			} catch(IOException | TimeoutException e) {
				m_View.setConnectProgress(0.0f);
				m_View.setConnectStatus("Disconnected", Color.red);
				m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.CONNECT);

				m_Logger.log(ILogger.Level.ERR, e);
				m_ConnectionFactory = null;
				m_Connection = null;
				m_Channel = null;
				return;
			}

			m_Agent = new ReferenceAgent(new DefaultAgentState(), m_AgentListener);
			m_StatusPanel.setAgent(m_Agent);
			m_View.setConnectProgress(1.0f);
			m_View.setConnectStatus(String.format("%s <===> %s (via %s)", m_QueueOk.getQueue(), DIRECT_EXCHANGE, routingKey), Color.black);
			m_View.setConnectionPanelState(AgentGUI.ConnectionPanelState.DISCONNECT);
		});
	}

	public void disconnect() {
		if(!isConnected()) {
			throw new IllegalStateException();
		}

		m_Agent.disconnect(AgentShutdown.Reason.Requested, -1);

		try {
			m_Connection.close();
		} catch(IOException e) {
			int x = 0;
		}

		m_ConnectionFactory = null;
		m_Connection = null;
		m_Channel = null;
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
		}
	}

	public static void main(String[] args) {
		try {
			UIManager.LookAndFeelInfo[] lf = UIManager.getInstalledLookAndFeels();
			for(javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
				//if("Nimbus".equals(info.getName())) {
				//if("CDE/Motif".equals(info.getName())) {
				//if("Windows".equals(info.getName())) {
				if("Windows Classic".equals(info.getName())) {
					//if("Metal".equals(info.getName())) {
					javax.swing.UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch(Throwable ex) {

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
		}
	}

	private void handleAck(long deliveryTag, boolean multiple) throws IOException {
		int x = 0;
	}

	private void handleNack(long deliveryTag, boolean multiple) throws IOException {
		int x = 0;
	}

	private void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
		AgentMessage msg = m_MessageBackend.fromBytes(body);
		if(msg == null) {
			throw new IOException("Message deserialisation failure");
		}

		if(m_Agent == null) {
			int x = 0;
		}

		UUID uuid = msg.getAgentUUID();

		if(m_Agent.getUUID() != null && !uuid.equals(m_Agent.getUUID())) {

			m_Channel.basicReject(envelope.getDeliveryTag(), false);

			/* If we've received a hello, send a terminate back */
			if(msg.getType() == AgentMessage.Type.Hello) {
				AgentHello hello = (AgentHello)msg;
				m_Logger.log(ILogger.Level.WARN, "Received agent.hello with key '%s', sending termination...", hello.queue);
				sendMessage(hello.queue, new AgentLifeControl(uuid, AgentLifeControl.Operation.Terminate));
			} else {
				m_Logger.log(ILogger.Level.WARN, "Ignoring message from unknown agent %s", uuid);
			}
			return;
		}

		try {
			m_Agent.processMessage(msg, Instant.now());
			m_Channel.basicAck(envelope.getDeliveryTag(), false);
		} catch(IllegalStateException e) {
			m_Logger.log(ILogger.Level.ERR, e);
			m_Channel.basicReject(envelope.getDeliveryTag(), false);
		}
	}

	private void sendMessage(String key, AgentMessage msg) throws IOException {
		byte[] bytes = m_MessageBackend.toBytes(msg);
		if(bytes == null) {
			throw new IOException("Message serialisation failure");
		}

		m_Channel.basicPublish(DIRECT_EXCHANGE, key, true, false, MessageProperties.PERSISTENT_BASIC, bytes);
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
		List<String> errors = new ArrayList<>();
		CompiledRun run;
		try {
			run = ANTLR4ParseAPIImpl.INSTANCE.parseRunToBuilder(jobText, errors).build();
		} catch(RunBuilder.RunfileBuildException | NullPointerException | PlanfileParseException e) {
			m_Logger.log(ILogger.Level.ERR, e);
			return;
		} finally {
			errors.forEach(s -> m_Logger.log(ILogger.Level.ERR, s));
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
		m_Agent.reset();
		m_StatusPanel.setJob(null);
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

	private class _ConfirmListener implements ConfirmListener {

		@Override
		public void handleAck(long deliveryTag, boolean multiple) throws IOException {
			Controller.this.handleAck(deliveryTag, multiple);
		}

		@Override
		public void handleNack(long deliveryTag, boolean multiple) throws IOException {
			Controller.this.handleNack(deliveryTag, multiple);
		}
	}

	private class _ReturnListener implements ReturnListener {

		@Override
		public void handleReturn(int arg0, String arg1, String arg2, String arg3, AMQP.BasicProperties arg4, byte[] arg5) throws IOException {
			int x = 0;
		}
	}

	private class _Consumer extends DefaultConsumer {

		public _Consumer(Channel channel) {
			super(channel);
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
			Controller.this.handleDelivery(consumerTag, envelope, properties, body);
		}
	}
}
