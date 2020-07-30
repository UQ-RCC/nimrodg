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
package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.MessageBackend;
import au.edu.uq.rcc.nimrodg.agent.messages.json.JsonBackend;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import javax.json.Json;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class AMQProcessorImpl implements AMQProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(Master.class);

	private final MessageQueueListener m_Listener;
	private final Connection m_Connection;
	private final Channel m_Channel;
	private final String m_DirectName;
	private final AMQP.Exchange.DeclareOk m_DirectExchangeOk;
	private final AMQP.Queue.DeclareOk m_QueueOk;
	private final _Consumer m_Consumer;

	private static final MessageBackend DEFAULT_MESSAGE_BACKEND = JsonBackend.INSTANCE;

	public AMQProcessorImpl(URI uri, Certificate[] certs, String tlsProtocol, String routingKey, boolean noVerifyPeer, boolean noVerifyHost, MessageQueueListener listener, ExecutorService execs) throws IOException, TimeoutException, URISyntaxException, GeneralSecurityException {
		m_Listener = listener;
		ConnectionFactory cf = new ConnectionFactory();

		String scheme = uri.getScheme();
		if(scheme == null) {
			scheme = "amqp";
		}

		/* Do this before setUri() */
		if(scheme.equalsIgnoreCase("amqps")) {
			/* Create an empty KeyStore and dump our certs in it. */
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, null);

			for(int i = 0; i < certs.length; ++i) {
				String name = String.format("nimrod%d", i);
				ks.setCertificateEntry(name, certs[i]);
			}

			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, null);

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ks);

			SSLContext cc = SSLContext.getInstance(tlsProtocol);
			cc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			cf.useSslProtocol(cc);
		} else if(!scheme.equalsIgnoreCase("amqp")) {
			throw new IllegalArgumentException("Invalid URI scheme");
		}

		cf.setUri(uri);
		cf.setSharedExecutor(execs);
		cf.setShutdownExecutor(execs);

		cf.setRequestedHeartbeat(30);
		cf.setAutomaticRecoveryEnabled(true);
		cf.setTopologyRecoveryEnabled(true);

		cf.setExceptionHandler(new ForgivingExceptionHandler() {
			@Override
			protected void log(String message, Throwable e) {
				super.log(message, e);
				LOGGER.error(message, e);
			}
		});

		m_Connection = cf.newConnection();

		m_Channel = m_Connection.createChannel();

		/* Don't schedule messages that haven't been ACK'd */
		m_Channel.basicQos(1);

		m_Channel.addConfirmListener(new _ConfirmListener());
		m_Channel.addReturnListener(new _ReturnListener());

		m_DirectName = "amq.direct";
		m_DirectExchangeOk = m_Channel.exchangeDeclare(m_DirectName, BuiltinExchangeType.DIRECT, true, false, false, null);
		m_QueueOk = m_Channel.queueDeclare("", true, true, true, null);

		/* No agents yet, don't bind anything to the direct exchange */
		m_Channel.queueBind(m_QueueOk.getQueue(), m_DirectName, routingKey);

		m_Consumer = new _Consumer(m_Channel);
		m_Channel.basicConsume(m_QueueOk.getQueue(), false, m_Consumer);
	}

	@Override
	public void close() throws IOException, TimeoutException {
		try(m_Connection) {
			//m_Channel.close();
			/* abort() will wait for the close to finish. */
			m_Channel.abort();
		} catch(AlreadyClosedException e) {
			// nop
		}
	}

	@Override
	public String getQueue() {
		return m_QueueOk.getQueue();
	}

	@Override
	public String getExchange() {
		return m_DirectName;
	}

	@Override
	public void sendMessage(String key, AgentMessage msg) throws IOException {
		byte[] bytes = DEFAULT_MESSAGE_BACKEND.toBytes(msg, StandardCharsets.UTF_8);
		if(bytes == null) {
			throw new IOException("Message serialisation failure");
		}

		m_Channel.basicPublish(m_DirectName, key, true, MessageProperties.PERSISTENT_BASIC, bytes);
	}

	private void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
		AgentMessage am = DEFAULT_MESSAGE_BACKEND.fromBytes(body, StandardCharsets.UTF_8);
		if(am == null) {
			throw new IOException("Message deserialisation failed");
		}

		long tag = envelope.getDeliveryTag();
		MessageQueueListener.MessageOperation op;
		try {
			op = m_Listener.processAgentMessage(am, body);
		} catch(IllegalStateException e) {
			op = MessageQueueListener.MessageOperation.RejectAndRequeue;
		} catch(IOException e) {
			m_Channel.basicReject(tag, true);
			throw e;
		}

		switch(op) {
			case Ack:
				m_Channel.basicAck(tag, false);
				break;
			case Reject:
				m_Channel.basicReject(tag, false);
				break;
			case RejectAndRequeue:
				m_Channel.basicReject(tag, true);
				break;
			case Terminate:
				m_Channel.basicAck(tag, false);

				if(am instanceof AgentHello) {
					AgentHello ah = (AgentHello)am;
					sendMessage(ah.queue, new AgentLifeControl(am.getAgentUUID(), AgentLifeControl.Operation.Terminate));
				} else {
					/* FIXME: Don't really know what to do here as we can't get the routing key :/ */
				}

				break;
			default:
				throw new IllegalStateException();
		}
	}

	private void handleAck(long deliveryTag, boolean multiple) throws IOException {
		//assert !multiple;

	}

	private void handleNack(long deliveryTag, boolean multiple) throws IOException {
		//throw new IllegalStateException("handleNack() called. We don't use nacks.");
	}

	private class _Consumer extends DefaultConsumer {

		public _Consumer(Channel channel) {
			super(channel);
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
			AMQProcessorImpl.this.handleDelivery(consumerTag, envelope, properties, body);
		}
	}

	private class _ConfirmListener implements ConfirmListener {

		@Override
		public void handleAck(long deliveryTag, boolean multiple) throws IOException {
			AMQProcessorImpl.this.handleAck(deliveryTag, multiple);
		}

		@Override
		public void handleNack(long deliveryTag, boolean multiple) throws IOException {
			AMQProcessorImpl.this.handleNack(deliveryTag, multiple);
		}
	}

	private class _ReturnListener implements ReturnListener {

		@Override
		public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body) throws IOException {
			String s = Json.createObjectBuilder()
					.add("replyCode", replyCode)
					.add("replyText", replyText)
					.add("exchange", exchange)
					.add("routingKey", routingKey)
					.add("properties", properties.toString())
					.add("body", new String(body, StandardCharsets.UTF_8))
					.build().toString();
			System.err.println(s);
		}
	}
}
