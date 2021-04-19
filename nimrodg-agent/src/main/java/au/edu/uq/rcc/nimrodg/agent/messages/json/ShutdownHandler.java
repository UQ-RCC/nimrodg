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

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;

import java.time.Instant;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

public class ShutdownHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid, Instant timestamp) {
		return new AgentShutdown.Builder()
				.agentUuid(uuid)
				.timestamp(timestamp)
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

	public static JsonString toJson(AgentInfo.ShutdownReason reason) {
		switch(reason) {
			case HostSignal:
				return Json.createValue("hostsignal");
			case Requested:
				return Json.createValue("requested");
		}

		throw new IllegalArgumentException();
	}

	public static AgentInfo.ShutdownReason readShutdownReason(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "hostsignal":
				return AgentInfo.ShutdownReason.HostSignal;
			case "requested":
				return AgentInfo.ShutdownReason.Requested;
		}

		throw new IllegalArgumentException();
	}
}
