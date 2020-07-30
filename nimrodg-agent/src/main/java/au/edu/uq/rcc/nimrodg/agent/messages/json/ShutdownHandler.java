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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;

import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

public class ShutdownHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid) {
		return new AgentShutdown.Builder()
				.agentUuid(uuid)
				.reason(readShutdownReason(jo.getString("reason")))
				.signal(jo.getInt("signal"))
				.build();
	}

	@Override
	public void write(JsonObjectBuilder jo, AgentMessage msg) {
		AgentShutdown as = (AgentShutdown)msg;
		jo.add("reason", toJson(as.reason));
		jo.add("signal", as.signal);
	}

	public static JsonString toJson(AgentShutdown.Reason reason) {
		switch(reason) {
			case HostSignal:
				return Json.createValue("hostsignal");
			case Requested:
				return Json.createValue("requested");
		}

		throw new IllegalArgumentException();
	}

	public static AgentShutdown.Reason readShutdownReason(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "hostsignal":
				return AgentShutdown.Reason.HostSignal;
			case "requested":
				return AgentShutdown.Reason.Requested;
		}

		throw new IllegalArgumentException();
	}
}
