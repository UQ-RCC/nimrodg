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

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CommandArgument;
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.ExecCommand;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.Substitution;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JsonUtils {

	/**
	 * Build a JSON structure representing a compiled experiment.
	 *
	 * @param cr          The compiled experiment.
	 * @param includeJobs Should the jobs be included? This is intended for batching, where the jobs will be added later.
	 * @return A {@link JsonObject} representing a compiled experiment.
	 */
	public static JsonObject toJson(CompiledRun cr, boolean includeJobs) {
		JsonObjectBuilder jb = Json.createObjectBuilder();

		jb.add("variables", Json.createArrayBuilder(cr.variables.stream().map(v -> v.name).collect(Collectors.toList())));
		jb.add("tasks", toJson(Collections.unmodifiableList(cr.tasks)));

		if(!includeJobs) {
			jb.add("jobs", JsonValue.EMPTY_JSON_ARRAY);
		} else if(!cr.jobs.isEmpty()) {
			jb.add("jobs", buildJobsJson(cr));
		}
		return jb.build();
	}

	public static JsonObject toJson(CompiledRun cr) {
		return toJson(cr, true);
	}

	/**
	 * Build a JSON array representing the list of jobs a compiled experiment.
	 *
	 * @param cr The compiled experiment.
	 * @return A {@link JsonArray} representing the list of jobs a compiled experiment.
	 */
	public static JsonArray buildJobsJson(CompiledRun cr) {
		JsonArrayBuilder jab = Json.createArrayBuilder();
		cr.jobs.forEach(j -> {
			JsonObjectBuilder job = Json.createObjectBuilder();
			for(int i = 0; i < j.indices.length; ++i) {
				job.add(cr.variables.get(i).name, cr.variables.get(i).supplier.getAt(j.indices[i]));
			}
			jab.add(job);
		});
		return jab.build();
	}

	public static JsonArray buildJobsJson(Collection<Map<String, String>> jobs) {
		JsonArrayBuilder jab = Json.createArrayBuilder();
		jobs.stream().map(JsonUtils::buildJobsJson).forEach(jab::add);
		return jab.build();
	}

	public static JsonObject buildJobsJson(Map<String, String> job) {
		JsonObjectBuilder jb = Json.createObjectBuilder();
		job.forEach(jb::add);
		return jb.build();
	}

	/**
	 * For each set of batchSize jobs in a compiled experiment, invoke proc.
	 *
	 * @param cr        The compiled experiment.
	 * @param batchSize The batch size. Must be >= 1.
	 * @param proc      The procedure to call for each batch.
	 */
	public static void withJobBatches(CompiledRun cr, int batchSize, Consumer<JsonArray> proc) {
		JsonArrayBuilder jab = Json.createArrayBuilder();
		JsonObjectBuilder job = Json.createObjectBuilder();

		int i = 0;
		for(CompiledJob j : cr.jobs) {
			for(int v = 0; v < j.indices.length; ++v) {
				job.add(cr.variables.get(v).name, cr.variables.get(v).supplier.getAt(j.indices[v]));
			}
			jab.add(job.build());
			++i;

			if(i == batchSize) {
				proc.accept(jab.build());
				i = 0;
			}
		}

		JsonArray last = jab.build();
		if(!last.isEmpty()) {
			proc.accept(last);
		}
	}

	public static JsonObject toJson(Collection<Task> tasks) {
		JsonObjectBuilder tb = Json.createObjectBuilder();

		tasks.forEach(t -> {
			JsonArrayBuilder ja = Json.createArrayBuilder();
			t.getCommands().forEach(c -> ja.add(toJson(c)));
			tb.add(Task.taskNameToString(t.getName()), ja.build());
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

	public static JsonObject toJson(Command cmd) {
		JsonObjectBuilder jb = Json.createObjectBuilder();

		switch(cmd.getType()) {
			case OnError: {
				OnErrorCommand ccmd = (OnErrorCommand)cmd;
				jb.add("type", "onerror");
				jb.add("action", OnErrorCommand.actionToString(ccmd.getAction()));
				break;
			}
			case Redirect: {
				RedirectCommand ccmd = (RedirectCommand)cmd;
				jb.add("type", "redirect");
				jb.add("stream", RedirectCommand.streamToString(ccmd.getStream()));
				jb.add("append", ccmd.getAppend());
				jb.add("file", toJson(ccmd.getFile()));
				break;
			}
			case Copy: {
				CopyCommand ccmd = (CopyCommand)cmd;
				jb.add("type", "copy");
				jb.add("source_context", CopyCommand.contextToString(ccmd.getSourceContext()));
				jb.add("source_path", toJson(ccmd.getSourcePath()));
				jb.add("destination_context", CopyCommand.contextToString(ccmd.getDestinationContext()));
				jb.add("destination_path", toJson(ccmd.getDestinationPath()));
				break;
			}
			case Exec: {
				ExecCommand ccmd = (ExecCommand)cmd;
				jb.add("type", "exec");
				jb.add("program", ccmd.getProgram() == null ? "" : ccmd.getProgram());
				jb.add("search_path", ccmd.searchPath());

				JsonArrayBuilder ja = Json.createArrayBuilder();
				ccmd.getArguments().forEach(c -> ja.add(toJson(c)));
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

	public static JsonObject toJson(CommandArgument arg) {
		JsonObjectBuilder jb = Json.createObjectBuilder();
		jb.add("text", arg.getText());

		JsonArrayBuilder ja = Json.createArrayBuilder();
		arg.getSubstitutions().forEach(s -> ja.add(toJson(s)));
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
				.add("name", sub.getVariable())
				.add("start", sub.getStartIndex())
				.add("end", sub.getEndIndex())
				.add("relative", sub.getRelativeStartIndex())
				.build();
	}

	public static CompiledSubstitution substitutionFromJson(JsonObject jo) {
		return new CompiledSubstitution(
				jo.getString("name"),
				jo.getInt("start"),
				jo.getInt("end"),
				jo.getInt("relative")
		);
	}

	public static List<String> stringListFromJson(JsonArray ja) {
		return ja.getValuesAs(JsonString.class).stream().map(JsonString::getString).collect(Collectors.toList());
	}

	private static String coalesceToString(JsonValue jv) {
		switch(jv.getValueType()) {
			case FALSE:
				return "false";
			case TRUE:
				return "true";
			case NUMBER:
				return jv.toString();
			case STRING:
				return ((JsonString)jv).getString();
		}

		throw new IllegalArgumentException();
	}

	public static List<Map<String, String>> jobsFileFromJson(JsonArray ja) {
		return ja.getValuesAs(JsonObject.class).stream()
				.map(JsonUtils::jobFromJson)
				.collect(Collectors.toList());
	}

	public static Map<String, String> jobFromJson(JsonObject s) {
		return s.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						v -> coalesceToString(v.getValue())
				));
	}
}
