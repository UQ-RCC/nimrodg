/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import java.util.UUID;
import java.time.Instant;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class PongHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid, Instant timestamp) {
		return new AgentPong.Builder()
				.agentUuid(uuid)
				.timestamp(timestamp)
				.clientState(Agent.clientStateFromString(jo.getString("state")))
				.build();
	}

	@Override
	public void write(JsonObjectBuilder jo, AgentMessage msg) {
		jo.add("state", Agent.clientStateToString(((AgentPong)msg).getState()));
	}

}
