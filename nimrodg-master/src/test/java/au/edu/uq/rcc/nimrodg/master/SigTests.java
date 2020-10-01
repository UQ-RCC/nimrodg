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

import au.edu.uq.rcc.nimrodg.agent.messages.AgentInit;
import au.edu.uq.rcc.nimrodg.master.sig.AuthHeader;
import au.edu.uq.rcc.nimrodg.master.sig.SigUtils;
import com.rabbitmq.client.AMQP;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SigTests {
	public static final String SECRET_KEY = "abc123";
	public static final String PAYLOAD = "{\"queue\":\"amq.gen-dScbYtY1SWZqJNt4qwZ7Mw\",\"timestamp\":\"2020-05-19T03:59:24.8710326Z\",\"type\":\"agent.hello\",\"uuid\":\"00000000-0000-0000-0000-000000000000\"}";
	public static final String HEADER_NULL = "NIM1-HMAC-NULL Credential=00000000000000000000000000000000/20200519T035924Z/0/nimrod, SignedProperties=app-id;content-encoding;content-type;delivery-mode;message-id;timestamp;type;user-id, SignedHeaders=user-agent;x-nimrodg-sent-at, Signature=";
	public static final String HEADER_SHA224 = "NIM1-HMAC-SHA224 Credential=00000000000000000000000000000000/20200519T035924Z/0/nimrod, SignedProperties=app-id;content-encoding;content-type;delivery-mode;message-id;timestamp;type;user-id, SignedHeaders=user-agent;x-nimrodg-sent-at, Signature=01326566e79fea3805be2c5f670c0334d63a6a2311330d2a9b41392a";
	public static final String HEADER_SHA256 = "NIM1-HMAC-SHA256 Credential=00000000000000000000000000000000/20200519T035924Z/0/nimrod, SignedProperties=app-id;content-encoding;content-type;delivery-mode;message-id;timestamp;type;user-id, SignedHeaders=user-agent;x-nimrodg-sent-at, Signature=ff261c65f718620d16dfecc2a5bf6d998e25951347bf845852599c7f5e7cadc3";
	public static final String HEADER_SHA384 = "NIM1-HMAC-SHA384 Credential=00000000000000000000000000000000/20200519T035924Z/0/nimrod, SignedProperties=app-id;content-encoding;content-type;delivery-mode;message-id;timestamp;type;user-id, SignedHeaders=user-agent;x-nimrodg-sent-at, Signature=dbd766d30cf5925f67d337d13d3242bfa012a609862d3c42c278019fe49c9ec28a500de4375cbf7c9333150a362e622f";
	public static final String HEADER_SHA512 = "NIM1-HMAC-SHA512 Credential=00000000000000000000000000000000/20200519T035924Z/0/nimrod, SignedProperties=app-id;content-encoding;content-type;delivery-mode;message-id;timestamp;type;user-id, SignedHeaders=user-agent;x-nimrodg-sent-at, Signature=aacd24ad2d130682e3a5f036a1511427cdc35cfc7cbae0e55457a38b17b65f52c9976dcabd14391d658d2c45ce4a14e95e017fd320679d82acc5b2869544da67";
	public static final long NONCE = 0;
	public static final Instant TIMESTAMP = Instant.ofEpochSecond(1589860764);
	public static final String APPID = SigUtils.DEFAULT_APPID;

	public static AMQP.BasicProperties makeBasicProperties(String appid, String authHeader) {
		return new AMQP.BasicProperties.Builder()
				.deliveryMode(2)
				.contentEncoding("identity")
				.contentType("application/json; charset=utf-8")
				.type("agent.hello")
				.timestamp(Date.from(TIMESTAMP))
				.userId("nimrod")
				.appId("nimrod")
				.messageId("8563a068-b116-1089-9078-110dcbd7b612")
				.headers(Map.of(
						"User-Agent", "NimrodG/1.4.0-41-g2b7c53f-DIRTY (x86_64-pc-linux-gnu)",
						"X-NimrodG-Sent-At", "2020-05-19T03:59:24.108116535Z",
						"Authorization", authHeader
				)).build();
	}

	@Test
	public void headerParseTest() {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA256);
		Assert.assertNotNull(hdr);
		Assert.assertEquals("NIM1-HMAC-SHA256", hdr.algorithm);
		Assert.assertEquals("00000000000000000000000000000000/20200519T035924Z/0/nimrod", hdr.credential);
		Assert.assertEquals("00000000000000000000000000000000", hdr.accessKey);
		Assert.assertEquals("20200519T035924Z", hdr.timestampString);
		Assert.assertEquals(TIMESTAMP, hdr.timestamp);
		Assert.assertEquals("nimrod", hdr.appid);
		/* Compare them as lists to check iteration order. AuthHeader should be using TreeSets. */
		Assert.assertEquals(
				List.of("app-id", "content-encoding", "content-type", "delivery-mode", "message-id", "timestamp", "type", "user-id"),
				new ArrayList<>(hdr.signedProperties)
		);
		Assert.assertEquals(List.of("user-agent", "x-nimrodg-sent-at"), new ArrayList<>(hdr.signedHeaders));
		Assert.assertEquals("ff261c65f718620d16dfecc2a5bf6d998e25951347bf845852599c7f5e7cadc3", hdr.signature);
		Assert.assertEquals(HEADER_SHA256, hdr.header);
	}

	@Test
	public void nullTest() throws NoSuchAlgorithmException {
		AuthHeader hdr = AuthHeader.parse(HEADER_NULL);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_NULL);

		Assert.assertNotNull(hdr);
		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);

		AuthHeader newAuth = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, SECRET_KEY, TIMESTAMP, NONCE, PAYLOAD.getBytes(StandardCharsets.UTF_8), newProps);

		Assert.assertEquals(hdr, newAuth);
	}

	@Test
	public void sha224Test() throws NoSuchAlgorithmException {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA224);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_SHA224);

		Assert.assertNotNull(hdr);
		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);

		AuthHeader newAuth = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, SECRET_KEY, TIMESTAMP, NONCE, PAYLOAD.getBytes(StandardCharsets.UTF_8), newProps);

		Assert.assertEquals(hdr, newAuth);
	}

	@Test
	public void sha256Test() throws NoSuchAlgorithmException {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA256);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_SHA256);

		Assert.assertNotNull(hdr);
		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);

		AuthHeader newAuth = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, SECRET_KEY, TIMESTAMP, NONCE, PAYLOAD.getBytes(StandardCharsets.UTF_8), newProps);

		Assert.assertEquals(hdr, newAuth);
	}

	@Test
	public void sha384Test() throws NoSuchAlgorithmException {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA384);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_SHA384);

		Assert.assertNotNull(hdr);
		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);

		AuthHeader newAuth = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, SECRET_KEY, TIMESTAMP, NONCE, PAYLOAD.getBytes(StandardCharsets.UTF_8), newProps);

		Assert.assertEquals(hdr, newAuth);
	}

	@Test
	public void sha512Test() throws NoSuchAlgorithmException {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA512);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_SHA512);

		Assert.assertNotNull(hdr);
		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);

		AuthHeader newAuth = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, SECRET_KEY, TIMESTAMP, NONCE, PAYLOAD.getBytes(StandardCharsets.UTF_8), newProps);

		Assert.assertEquals(hdr, newAuth);
	}

	@Test
	public void validationTest() {
		AuthHeader hdr = AuthHeader.parse(HEADER_SHA256);
		AMQP.BasicProperties props = makeBasicProperties(APPID, HEADER_SHA256);

		Assert.assertNotNull(hdr);

		/* Valid message, exact times */
		Assert.assertTrue(SigUtils.validateMessage(props, hdr, new AgentInit.Builder()
				.agentUuid(UUID.randomUUID())
				.timestamp(hdr.timestamp)
				.build(), SigUtils.DEFAULT_APPID
		));

		/* Valid message, with subsecond drift. */
		Assert.assertTrue(SigUtils.validateMessage(props, hdr, new AgentInit.Builder()
				.agentUuid(UUID.randomUUID())
				.timestamp(hdr.timestamp.plusMillis(500))
				.build(), SigUtils.DEFAULT_APPID
		));

		/* Invalid message, timestamp out of range. */
		Assert.assertFalse(SigUtils.validateMessage(props, hdr, new AgentInit.Builder()
				.agentUuid(UUID.randomUUID())
				.timestamp(hdr.timestamp.plusSeconds(1))
				.build(), SigUtils.DEFAULT_APPID
		));

		/* Invalid message, differing appid. */
		Assert.assertFalse(SigUtils.validateMessage(props, hdr, new AgentInit.Builder()
				.agentUuid(UUID.randomUUID())
				.timestamp(hdr.timestamp)
				.build(), "badapp"
		));

	}
}
