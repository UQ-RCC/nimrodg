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
package au.edu.uq.rcc.nimrodg.agent.messages.json;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.MessageBackend;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;
import javax.mail.internet.ContentType;
import javax.mail.internet.ParameterList;

public class JsonBackend implements MessageBackend {
	private static final Map<AgentMessage.Type, JsonHandler> MESSAGE_HANDLERS = Map.of(
			AgentMessage.Type.Hello, new HelloHandler(),
			AgentMessage.Type.Init, new InitHandler(),
			AgentMessage.Type.LifeControl, new LifeControlHandler(),
			AgentMessage.Type.Shutdown, new ShutdownHandler(),
			AgentMessage.Type.Submit, new SubmitHandler(),
			AgentMessage.Type.Update, new UpdateHandler(),
			AgentMessage.Type.Ping, new PingHandler(),
			AgentMessage.Type.Pong, new PongHandler()
	);

	private static final ContentType CONTENT_TYPE = new ContentType("application", "json", new ParameterList());
	private static final JsonReaderFactory READER_FACTORY = Json.createReaderFactory(Map.of());
	public static final JsonBackend INSTANCE = new JsonBackend();

	private static JsonHandler getHandlerForType(AgentMessage.Type type) {
		JsonHandler h = MESSAGE_HANDLERS.getOrDefault(type, null);
		if(h == null) {
			throw new UnsupportedOperationException();
		}

		return h;
	}

	@Override
	public byte[] toBytes(AgentMessage msg, Charset charset) {
		return toJson(msg).toString().getBytes(charset);
	}

	public JsonObject toJson(AgentMessage msg) {
		JsonObjectBuilder jo = Json.createObjectBuilder();
		jo.add("uuid", msg.getAgentUUID().toString());
		jo.add("version", msg.getVersion());
		jo.add("type", toJson(msg.getType()));
		jo.add("timestamp", DateTimeFormatter.ISO_INSTANT.format(msg.getTimestamp()));
		getHandlerForType(msg.getType()).write(jo, msg);
		return jo.build();
	}

	@Override
	public AgentMessage fromBytes(byte[] bytes, Charset charset) {
		JsonObject jo;
		try(JsonReader p = READER_FACTORY.createReader(new ByteArrayInputStream(bytes), charset)) {
			jo = p.readObject();
		}

		/* Do a simple version check. */
		int version = 1;
		JsonNumber jver = jo.getJsonNumber("version");
		if(jver != null) {
			version = jver.intValue();
		}

		if(version != AgentMessage.PROTOCOL_VERSION) {
			return null;
		}

		return getHandlerForType(readMessageType(jo.getString("type"))).read(
				jo,
				UUID.fromString(jo.getString("uuid")),
				Instant.from(DateTimeFormatter.ISO_INSTANT.parse(jo.getString("timestamp")))
		);
	}

	/* TODO: Move this out of here if there's ever more than one message backend. */
	private static ContentType duplicateContentType(ContentType ct) {
		ParameterList parms = new ParameterList();
		ct.getParameterList().getNames().asIterator().forEachRemaining(s -> {
			parms.set(s, ct.getParameterList().get(s));
		});

		return new ContentType(ct.getPrimaryType(), ct.getSubType(), parms);
	}

	@Override
	public ContentType getContentType() {
		return duplicateContentType(CONTENT_TYPE);
	}

	public static JsonString toJson(AgentMessage.Type type) {
		return Json.createValue(type.typeString);
	}

	public static AgentMessage.Type readMessageType(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "agent.hello":
				return AgentMessage.Type.Hello;
			case "agent.init":
				return AgentMessage.Type.Init;
			case "agent.lifecontrol":
				return AgentMessage.Type.LifeControl;
			case "agent.query":
				return AgentMessage.Type.Query;
			case "agent.shutdown":
				return AgentMessage.Type.Shutdown;
			case "agent.submit":
				return AgentMessage.Type.Submit;
			case "agent.update":
				return AgentMessage.Type.Update;
			case "agent.ping":
				return AgentMessage.Type.Ping;
			case "agent.pong":
				return AgentMessage.Type.Pong;
		}
		throw new IllegalArgumentException();
	}
}
