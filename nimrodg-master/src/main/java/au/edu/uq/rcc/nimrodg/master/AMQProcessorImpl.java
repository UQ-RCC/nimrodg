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

import au.edu.uq.rcc.nimrodg.agent.MessageBackend;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.json.JsonBackend;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.client.impl.ForgivingExceptionHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.json.Json;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParseException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

public class AMQProcessorImpl implements AMQProcessor {

	private static final Logger LOGGER = LogManager.getLogger(Master.class);

	private final String m_User;
	private final MessageQueueListener m_Listener;
	private final Connection m_Connection;
	private final Channel m_Channel;
	private final String m_DirectName;
	private final AMQP.Exchange.DeclareOk m_DirectExchangeOk;
	private final AMQP.Queue.DeclareOk m_QueueOk;
	private final _Consumer m_Consumer;

	private static final MessageBackend DEFAULT_MESSAGE_BACKEND = JsonBackend.INSTANCE;

	private static final Map<String, MessageBackend> MESSAGE_BACKENDS = Map.of(
			DEFAULT_MESSAGE_BACKEND.getContentType().getBaseType(), DEFAULT_MESSAGE_BACKEND
	);

	public AMQProcessorImpl(URI uri, Certificate[] certs, String tlsProtocol, String routingKey, boolean noVerifyPeer, boolean noVerifyHost, MessageQueueListener listener, ExecutorService execs) throws IOException, TimeoutException, URISyntaxException, GeneralSecurityException {
		m_Listener = listener;
		ConnectionFactory cf = new ConnectionFactory();

		String scheme = uri.getScheme();
		if(scheme == null) {
			scheme = "amqp";
		}

		Optional<String> user = ShellUtils.getUriUser(uri);
		if(!user.isPresent()) {
			throw new IllegalArgumentException();
		}
		
		m_User = user.get();

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
				LOGGER.log(Level.ERROR, e);
			}
		});

		m_Connection = cf.newConnection();

		m_Channel = m_Connection.createChannel();
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
	public void close() throws IOException {
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
	public AMQPMessage sendMessage(String key, AgentMessage msg) throws IOException {
		Charset cs = StandardCharsets.UTF_8;
		byte[] bytes = DEFAULT_MESSAGE_BACKEND.toBytes(msg, cs);
		if(bytes == null) {
			throw new IOException("Message serialisation failure");
		}

		ContentType ct = DEFAULT_MESSAGE_BACKEND.getContentType();
		ct.setParameter("charset", cs.name());

		UUID messageId = UUID.randomUUID();
		Instant timestamp = Instant.now();
		AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
				.deliveryMode(2)
				.contentType(ct.toString())
				.contentEncoding("identity")
				.type(msg.getTypeString())
				.messageId(messageId.toString())
				.timestamp(Date.from(timestamp))
				.userId(m_User)
				.appId("nimrod")
				.headers(Map.of(
						"User-Agent", "NimrodGMaster/X.X.X", /* FIXME */
						"X-NimrodG-Sent-At", timestamp.toString()
				))
				.build();

		
		m_Channel.basicPublish(m_DirectName, key, true, props, bytes);

		return new AMQPMessage(
				bytes,
				props,
				messageId,
				ct,
				cs,
				timestamp,
				msg
		);
	}

	private Charset resolveCharset(String name) {
		/* Following HTTP/1.1 here: https://tools.ietf.org/html/rfc2616 section 3.4.1*/
		if(name == null) {
			return StandardCharsets.ISO_8859_1;
		}

		try {
			return Charset.forName(name);
		} catch(UnsupportedCharsetException e) {
			return null;
		}
	}

	private void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
		long tag = envelope.getDeliveryTag();

		if(!Optional.ofNullable(properties.getAppId()).map("nimrod"::equals).orElse(false)) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		if(properties.getMessageId() == null) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		UUID uuid;

		try {
			uuid = UUID.fromString(properties.getMessageId());
		} catch(IllegalArgumentException e) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		if(properties.getContentType() == null) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		ContentType contentType;
		try {
			contentType = new ContentType(properties.getContentType());
		} catch(ParseException e) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		MessageBackend mb = MESSAGE_BACKENDS.get(contentType.getBaseType());
		if(mb == null) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		Charset charset = resolveCharset(contentType.getParameter("charset"));

		if(charset == null) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		if(properties.getTimestamp() == null) {
			opMessage(MessageQueueListener.MessageOperation.Reject, tag);
			return;
		}

		AgentMessage am = mb.fromBytes(body, charset);
		if(am == null) {
			throw new IOException("Message deserialisation failed");
		}

		AMQPMessage amsg = new AMQPMessage(
				body,
				properties,
				uuid,
				contentType,
				charset,
				properties.getTimestamp().toInstant(),
				am
		);
		Optional<MessageQueueListener.MessageOperation> op;
		try {
			op = m_Listener.processAgentMessage(tag, amsg);
		} catch(IllegalStateException e) {
			op = Optional.of(MessageQueueListener.MessageOperation.Terminate);
		}

		if(op.isPresent()) {
			opMessage(op.get(), tag);
			if(op.get() == MessageQueueListener.MessageOperation.Terminate) {
				opMessage(MessageQueueListener.MessageOperation.Ack, tag);
				if(am instanceof AgentHello) {
					AgentHello ah = (AgentHello)am;
					sendMessage(ah.queue, new AgentLifeControl(am.getAgentUUID(), AgentLifeControl.Operation.Terminate));
				} else {
					/* FIXME: Don't really know what to do here as we can't get the routing key :/ */
				}
			}
		}
	}

	@Override
	public void opMessage(MessageQueueListener.MessageOperation op, long tag) {
		synchronized(m_Channel) {
			try {
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
						break;
				}
			} catch(IOException e) {
				LOGGER.catching(e);
			}
		}
	}

	private class _Consumer extends DefaultConsumer {

		_Consumer(Channel channel) {
			super(channel);
		}

		@Override
		public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
			AMQProcessorImpl.this.handleDelivery(consumerTag, envelope, properties, body);
		}
	}

	private static class _ConfirmListener implements ConfirmListener {

		@Override
		public void handleAck(long deliveryTag, boolean multiple) throws IOException {
			/* nop */
		}

		@Override
		public void handleNack(long deliveryTag, boolean multiple) throws IOException {
			/* nop */
		}
	}

	private static class _ReturnListener implements ReturnListener {

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
