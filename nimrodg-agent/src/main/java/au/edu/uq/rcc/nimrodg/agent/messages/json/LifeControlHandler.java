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

import au.edu.uq.rcc.nimrodg.agent.messages.AgentLifeControl;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;

import java.time.Instant;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

public class LifeControlHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid, Instant timestamp) {
		return new AgentLifeControl.Builder()
				.agentUuid(uuid)
				.timestamp(timestamp)
				.operation(readLifeControlOperation(jo.getString("operation")))
				.build();
	}

	@Override
	public void write(JsonObjectBuilder jo, AgentMessage msg) {
		jo.add("operation", toJson(((AgentLifeControl)msg).operation));
	}

	private static JsonString toJson(AgentLifeControl.Operation op) {
		switch(op) {
			case Cancel:
				return Json.createValue("cancel");
			case Terminate:
				return Json.createValue("terminate");
		}

		throw new IllegalArgumentException();
	}

	private static AgentLifeControl.Operation readLifeControlOperation(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "cancel":
				return AgentLifeControl.Operation.Cancel;
			case "terminate":
				return AgentLifeControl.Operation.Terminate;
		}

		throw new IllegalArgumentException();
	}

}
