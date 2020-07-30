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
import au.edu.uq.rcc.nimrodg.agent.messages.json.JsonBackend;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface MessageBackend {
	/**
	 * Encode an agent message to bytes using a given Charset.
	 *
	 * @param msg     The message to send. May not be null.
	 * @param charset The charset to encode the message with. May not be null.
	 * @return The charset-encoded byte representation of the message.
	 */
	byte[] toBytes(AgentMessage msg, Charset charset);

	/**
	 * Decode an agent message from bytes using a given Charset.
	 *
	 * @param bytes   The bytes to decode. May not be null.
	 * @param charset The charset to decode the message with. May not be null.
	 * @return The agent message represented by the bytes.
	 */
	AgentMessage fromBytes(byte[] bytes, Charset charset);

	@Deprecated
	default byte[] toBytes(AgentMessage msg) {
		return toBytes(msg, StandardCharsets.UTF_8);
	}

	@Deprecated
	default AgentMessage fromBytes(byte[] bytes) {
		return fromBytes(bytes, StandardCharsets.UTF_8);
	}

	@Deprecated
	static MessageBackend createBackend() {
		return JsonBackend.INSTANCE;
	}
}
