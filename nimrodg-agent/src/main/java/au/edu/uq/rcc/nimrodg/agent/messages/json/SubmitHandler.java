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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

public class SubmitHandler implements JsonHandler {

	@Override
	public AgentMessage read(JsonObject jo, UUID uuid, Instant timestamp) {
		JsonObject joo = jo.getJsonObject("job");

		UUID jobUuid = UUID.fromString(joo.getString("uuid"));

		long index = joo.getJsonNumber("index").longValue();

		String txuri = joo.getJsonString("txuri").getString();

		Map<String, String> env = new HashMap<>();
		JsonObject je = joo.getJsonObject("environment");

		for(Map.Entry<String, JsonValue> e : je.entrySet()) {
			env.put(e.getKey(), ((JsonString)e).getString());
		}

		List<NetworkJob.ResolvedCommand> commands = new ArrayList<>();
		JsonArray jc = joo.getJsonArray("commands");
		for(JsonValue j : jc) {
			commands.add(readCommand(j.asJsonObject()));
		}

		return new AgentSubmit.Builder()
				.agentUuid(uuid)
				.timestamp(timestamp)
				.job(new NetworkJob(jobUuid, index, txuri, commands, env))
				.build();
	}

	private NetworkJob.ResolvedCommand readCommand(JsonObject jo) {
		/* We rely on this to throw if the command type is bad */
		Command.Type type = stringToCommandType(jo.getString("type"));

		switch(type) {
			case OnError: {
				return new NetworkJob.OnErrorCommand(stringToOnErrorAction(jo.getString("action")));
			}
			case Redirect: {
				return new NetworkJob.RedirectCommand(
						stringToStream(jo.getString("stream")),
						jo.getBoolean("append"),
						jo.getString("file")
				);
			}
			case Copy: {
				return new NetworkJob.CopyCommand(
						stringToContext(jo.getString("source_context")),
						jo.getString("source_path"),
						stringToContext(jo.getString("destination_context")),
						jo.getString("destination_path")
				);
			}
			case Exec: {
				JsonArray ja = jo.getJsonArray("arguments");

				List<String> args = new ArrayList<>();
				for(JsonValue s : ja) {
					args.add(((JsonString)s).getString());
				}

				return new NetworkJob.ExecCommand(jo.getString("program"), args, jo.getBoolean("search_path"));
			}
		}

		throw new IllegalArgumentException();
	}

	@Override
	public void write(JsonObjectBuilder jo, AgentMessage msg) {
		AgentSubmit as = (AgentSubmit)msg;

		JsonObjectBuilder joo = Json.createObjectBuilder();
		NetworkJob j = as.getJob();

		joo.add("uuid", j.uuid.toString());
		joo.add("index", j.index);
		joo.add("txuri", j.txUri);

		JsonObjectBuilder je = Json.createObjectBuilder();
		Map<String, String> env = j.environment;
		for(String s : env.keySet()) {
			je.add(s, env.get(s));
		}
		joo.add("environment", je);

		JsonArrayBuilder jc = Json.createArrayBuilder();
		j.commands.forEach(jb -> write(jc, jb));
		joo.add("commands", jc);

		jo.add("job", joo);
	}

	private void write(JsonArrayBuilder ja, NetworkJob.ResolvedCommand cmd) {
		JsonObjectBuilder jo = Json.createObjectBuilder();

		jo.add("type", toJson(cmd.getType()));
		switch(cmd.getType()) {
			case OnError: {
				NetworkJob.OnErrorCommand ccmd = (NetworkJob.OnErrorCommand)cmd;
				jo.add("action", toJson(ccmd.action));
				break;
			}
			case Redirect: {
				NetworkJob.RedirectCommand ccmd = (NetworkJob.RedirectCommand)cmd;
				jo.add("stream", toJson(ccmd.stream));
				jo.add("append", ccmd.append);
				jo.add("file", ccmd.file);
				break;
			}
			case Copy: {
				NetworkJob.CopyCommand ccmd = (NetworkJob.CopyCommand)cmd;
				jo.add("source_context", toJson(ccmd.sourceContext));
				jo.add("source_path", ccmd.sourcePath);
				jo.add("destination_context", toJson(ccmd.destinationContext));
				jo.add("destination_path", ccmd.destinationPath);
				break;
			}
			case Exec: {
				NetworkJob.ExecCommand ccmd = (NetworkJob.ExecCommand)cmd;

				jo.add("program", ccmd.program);
				JsonArrayBuilder jac = Json.createArrayBuilder();
				List<String> components = ccmd.arguments;
				for(String s : components) {
					jac.add(s);
				}
				jo.add("arguments", jac);
				jo.add("search_path", ccmd.searchPath);
				break;
			}
		}

		ja.add(jo);
	}

	public static JsonString toJson(Command.Type type) {
		switch(type) {
			case OnError:
				return Json.createValue("onerror");
			case Redirect:
				return Json.createValue("redirect");
			case Copy:
				return Json.createValue("copy");
			case Exec:
				return Json.createValue("exec");
		}

		throw new IllegalArgumentException();
	}

	public static Command.Type stringToCommandType(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "onerror":
				return Command.Type.OnError;
			case "redirect":
				return Command.Type.Redirect;
			case "copy":
				return Command.Type.Copy;
			case "exec":
				return Command.Type.Exec;
		}

		throw new IllegalArgumentException();
	}

	public static JsonString toJson(OnErrorCommand.Action a) {
		switch(a) {
			case Fail:
				return Json.createValue("fail");
			case Ignore:
				return Json.createValue("ignore");
		}
		throw new IllegalArgumentException();
	}

	public static OnErrorCommand.Action stringToOnErrorAction(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "fail":
				return OnErrorCommand.Action.Fail;
			case "ignore":
				return OnErrorCommand.Action.Ignore;
		}

		throw new IllegalArgumentException();
	}

	public static JsonString toJson(CopyCommand.Context ctx) {
		switch(ctx) {
			case Node:
				return Json.createValue("node");
			case Root:
				return Json.createValue("root");
		}

		throw new IllegalArgumentException();
	}

	public static CopyCommand.Context stringToContext(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "node":
				return CopyCommand.Context.Node;
			case "root":
				return CopyCommand.Context.Root;
		}

		throw new IllegalArgumentException();
	}

	public static JsonString toJson(RedirectCommand.Stream s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case Stderr:
				return Json.createValue("stderr");
			case Stdout:
				return Json.createValue("stdout");
		}

		throw new IllegalArgumentException();
	}

	public static RedirectCommand.Stream stringToStream(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "stderr":
				return RedirectCommand.Stream.Stderr;
			case "stdout":
				return RedirectCommand.Stream.Stdout;
		}
		throw new IllegalArgumentException();
	}
}
