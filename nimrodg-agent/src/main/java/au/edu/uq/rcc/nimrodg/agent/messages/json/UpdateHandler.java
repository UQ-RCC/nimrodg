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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.CommandResult;

import java.time.Instant;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

public class UpdateHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid, Instant timestamp) {
		return new AgentUpdate.Builder()
				.agentUuid(uuid)
				.timestamp(timestamp)
				.jobUuid(UUID.fromString(jo.getString("job_uuid")))
				.commandResult(readCommandResult(jo.getJsonObject("command_result")))
				.action(stringToUpdateAction(jo.getString("action")))
				.build();
	}

	private AgentUpdate.CommandResult_ readCommandResult(JsonObject jo) {
		return new AgentUpdate.CommandResult_(
				stringToCommandResultStatus(jo.getString("status")),
				jo.getJsonNumber("index").longValue(),
				(float)jo.getJsonNumber("time").doubleValue(),
				jo.getInt("retval"),
				jo.getString("message"),
				jo.getInt("error_code")
		);
	}

	@Override
	public void write(JsonObjectBuilder jo, AgentMessage msg) {
		AgentUpdate au = (AgentUpdate)msg;

		jo.add("job_uuid", au.getJobUUID().toString());
		jo.add("action", toJson(au.getAction()));

		JsonObjectBuilder joo = Json.createObjectBuilder();

		{
			AgentUpdate.CommandResult_ res = au.getCommandResult();
			joo.add("status", toJson(res.status));
			joo.add("index", res.index);
			joo.add("time", res.time);
			joo.add("retval", res.retVal);
			joo.add("message", res.message);
			joo.add("error_code", res.errorCode);
		}
		jo.add("command_result", joo);
	}

	public static JsonString toJson(AgentUpdate.Action a) {
		switch(a) {
			case Continue:
				return Json.createValue("continue");
			case Stop:
				return Json.createValue("stop");
		}

		throw new IllegalArgumentException();
	}

	public static AgentUpdate.Action stringToUpdateAction(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "continue":
				return AgentUpdate.Action.Continue;
			case "stop":
				return AgentUpdate.Action.Stop;
		}

		throw new IllegalArgumentException();
	}

	public static JsonString toJson(CommandResult.CommandResultStatus s) {
		switch(s) {
			case PRECONDITION_FAILURE:
				return Json.createValue("precondition_failure");
			case EXCEPTION:
				return Json.createValue("exception");
			case SUCCESS:
				return Json.createValue("success");
			case SYSTEM_ERROR:
				return Json.createValue("system_error");
			case ABORTED:
				return Json.createValue("aborted");
			case FAILED:
				return Json.createValue("failed");
		}

		throw new IllegalArgumentException();
	}

	public static CommandResult.CommandResultStatus stringToCommandResultStatus(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "precondition_failure":
				return CommandResult.CommandResultStatus.PRECONDITION_FAILURE;
			case "exception":
				return CommandResult.CommandResultStatus.EXCEPTION;
			case "success":
				return CommandResult.CommandResultStatus.SUCCESS;
			case "system_error":
				return CommandResult.CommandResultStatus.SYSTEM_ERROR;
			case "aborted":
				return CommandResult.CommandResultStatus.ABORTED;
			case "failed":
				return CommandResult.CommandResultStatus.FAILED;
		}

		throw new IllegalArgumentException();
	}

}
