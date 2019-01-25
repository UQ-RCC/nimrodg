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
package au.edu.uq.rcc.nimrodg.api.utils.run;

import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.Substitution;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

public class JsonUtils {

	public static JsonObject toJson(CompiledRun cr) {
		JsonObjectBuilder jb = Json.createObjectBuilder();

		JsonArrayBuilder vb = Json.createArrayBuilder();
		cr.variables.forEach(v -> {
			vb.add(Json.createObjectBuilder()
					.add("name", v.name)
					.add("values", Json.createArrayBuilder(v.values).build())
			);
		});
		jb.add("variables", vb.build());

		jb.add("tasks", toJson(cr.tasks));

		if(!cr.tasks.isEmpty()) {
			JsonArrayBuilder jab = Json.createArrayBuilder();
			cr.jobs.forEach(j -> jab.add(Json.createArrayBuilder(Arrays.stream(j.indices).boxed().collect(Collectors.toList())).build()));
			jb.add("jobs", jab.build());
		}
		return jb.build();
	}

	public static JsonObject toJson(List<CompiledTask> tasks) {
		JsonObjectBuilder tb = Json.createObjectBuilder();

		tasks.forEach(t -> {
			JsonArrayBuilder ja = Json.createArrayBuilder();
			t.commands.forEach(c -> ja.add(toJson(c)));
			tb.add(Task.taskNameToString(t.name), ja.build());
		});
		return tb.build();
	}

	public static List<CompiledTask> taskListFromJson(JsonObject tasks) {
		return tasks.keySet().stream().map(name
				-> new CompiledTask(
						Task.stringToTaskName(name),
						tasks.getJsonArray(name).stream()
								.map(a -> compiledCommandFromJson(a.asJsonObject()))
								.collect(Collectors.toList())
				)).collect(Collectors.toList());
	}

	public static JsonObject toJson(CompiledCommand cmd) {
		JsonObjectBuilder jb = Json.createObjectBuilder();

		switch(cmd.type) {
			case OnError: {
				CompiledOnErrorCommand ccmd = (CompiledOnErrorCommand)cmd;
				jb.add("type", "onerror");
				jb.add("action", OnErrorCommand.actionToString(ccmd.action));
				break;
			}
			case Redirect: {
				CompiledRedirectCommand ccmd = (CompiledRedirectCommand)cmd;
				jb.add("type", "redirect");
				jb.add("stream", RedirectCommand.streamToString(ccmd.stream));
				jb.add("append", ccmd.append);
				jb.add("file", toJson(ccmd.file));
				break;
			}
			case Copy: {
				CompiledCopyCommand ccmd = (CompiledCopyCommand)cmd;
				jb.add("type", "copy");
				jb.add("source_context", CopyCommand.contextToString(ccmd.sourceContext));
				jb.add("source_path", toJson(ccmd.sourcePath));
				jb.add("destination_context", CopyCommand.contextToString(ccmd.destContext));
				jb.add("destination_path", toJson(ccmd.destPath));
				break;
			}
			case Exec: {
				CompiledExecCommand ccmd = (CompiledExecCommand)cmd;
				jb.add("type", "exec");
				jb.add("program", ccmd.program == null ? "" : ccmd.program);
				jb.add("search_path", ccmd.searchPath);

				JsonArrayBuilder ja = Json.createArrayBuilder();
				ccmd.arguments.forEach(c -> ja.add(toJson(c)));
				jb.add("arguments", ja.build());
				break;
			}
			default:
				throw new IllegalArgumentException();
		}

		return jb.build();
	}

	public static CompiledCommand compiledCommandFromJson(JsonObject jo) {
		String stype = jo.getString("type");
		switch(stype) {
			case "onerror":
				return new CompiledOnErrorCommand(OnErrorCommand.stringToAction(jo.getString("action")));
			case "redirect":
				return new CompiledRedirectCommand(
						RedirectCommand.stringToStream(jo.getString("stream")),
						jo.getBoolean("append"),
						compiledArgumentFromJson(jo.getJsonObject("file"))
				);
			case "copy":
				return new CompiledCopyCommand(
						CopyCommand.stringToContext(jo.getString("source_context")),
						compiledArgumentFromJson(jo.getJsonObject("source_path")),
						CopyCommand.stringToContext(jo.getString("destination_context")),
						compiledArgumentFromJson(jo.getJsonObject("destination_path"))
				);
			case "exec":
				return new CompiledExecCommand(
						jo.getString("program"),
						jo.getJsonArray("arguments").stream().map(v -> compiledArgumentFromJson(v.asJsonObject())).collect(Collectors.toList()),
						jo.getBoolean("search_path")
				);
			default:
				throw new IllegalArgumentException();
		}
	}

	public static JsonObject toJson(CompiledArgument arg) {
		JsonObjectBuilder jb = Json.createObjectBuilder();
		jb.add("text", arg.text);

		JsonArrayBuilder ja = Json.createArrayBuilder();
		arg.substitutions.forEach(s -> ja.add(toJson(s)));
		jb.add("substitutions", ja.build());
		return jb.build();
	}

	public static CompiledArgument compiledArgumentFromJson(JsonObject jo) {
		return new CompiledArgument(
				jo.getString("text"),
				jo.getJsonArray("substitutions").stream()
						.map(v -> substitutionFromJson(v.asJsonObject()))
						.collect(Collectors.toList())
		);
	}

	public static JsonObject toJson(Substitution sub) {
		return Json.createObjectBuilder()
				.add("name", sub.variable())
				.add("start", sub.startIndex())
				.add("end", sub.endIndex())
				.add("relative", sub.relativeStartIndex())
				.build();
	}

	public static Substitution substitutionFromJson(JsonObject jo) {
		return new Substitution(
				jo.getString("name"),
				jo.getInt("start"),
				jo.getInt("end"),
				jo.getInt("relative")
		);
	}

	public static List<String> stringListFromJson(JsonArray ja) {
		return ja.getValuesAs(JsonString.class).stream().map(s -> s.getString()).collect(Collectors.toList());
	}

	private static String coalesceToString(JsonValue jv) {
		switch(jv.getValueType()) {
			case FALSE:
				return "false";
			case TRUE:
				return "true";
			case NUMBER:
				return ((JsonNumber)jv).toString();
			case STRING:
				return ((JsonString)jv).getString();
		}

		throw new IllegalArgumentException();
	}

	public static List<Map<String, String>> jobsFileFromJson(JsonArray ja) {
		return ja.getValuesAs(JsonObject.class).stream()
				.map(s -> s.entrySet().stream()
				.collect(Collectors.toMap(
						e -> e.getKey(),
						v -> coalesceToString(v.getValue())
				))
				).collect(Collectors.toList());
	}
}
