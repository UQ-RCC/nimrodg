package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class QueryCommand extends JobCommand<QueryCommand.Response> {
	private final Map<String, Actuator.AgentStatus> stateMap;

	private QueryCommand(String[] argv, CommandParser parser, boolean appendJobIds, Map<String, Actuator.AgentStatus> stateMap) {
		super(argv, parser, appendJobIds);
		this.stateMap = stateMap;
	}

	@Override
	protected Response processPayload(RemoteShell.CommandResult cr, Map<String, Object> vars, Map<String, JsonValue> payload) {
		String[] jobIds = (String[])vars.get("jobids");

		Map<String, JobQueryInfo> rm = new HashMap<>(jobIds.length);
		for(String jobid : jobIds) {
			JsonValue jv = payload.get(jobid);
			if(jv == null || jv.getValueType() != JsonValue.ValueType.OBJECT) {
				rm.put(jobid, new JobQueryInfo(jobid, Actuator.AgentStatus.Unknown, JsonValue.EMPTY_JSON_OBJECT));
				continue;
			}
			JsonObject attrs = jv.asJsonObject();

			/* FIXME: Hardcoded, should allow the user to specify? */
			JsonValue val = attrs.get("state");
			if(val == null) {
				val = JsonValue.NULL;
			}

			Actuator.AgentStatus status;

			switch(val.getValueType()) {
				case STRING:
					status = mapState(((JsonString)val).getString());
					break;
				case TRUE:
				case FALSE:
				case NUMBER:
					status = mapState(val.toString());
					break;
				default:
					status = Actuator.AgentStatus.Unknown;
			}

			rm.put(jobid, new JobQueryInfo(jobid, status, attrs));
		}

		return new Response(cr, rm);
	}

	private Actuator.AgentStatus mapState(String s) {
		return stateMap.getOrDefault(s, Actuator.AgentStatus.Unknown);
	}

	@Override
	public JsonObjectBuilder toJson() {
		JsonObjectBuilder job = Json.createObjectBuilder();
		stateMap.forEach((k, v) -> job.add(k, v.toString()));
		return super.toJson().add("state_map", job);
	}

	public static Builder fromJson(JsonObject jo) {
		Objects.requireNonNull(jo, "jo");

		Builder b = new Builder();
		JobCommand.fromJson(b, jo);

		JsonObject jr = jo.getJsonObject("response");
		switch(jr.getString("type")) {
			case "line_regex":
				b.parser(LineRegexParser.fromJson(jr, "jobid", 0));
				break;
			case "json":
				b.parser(JsonParser.fromJson(jr, "jobid"));
				break;
			case "none":
				b.parser(NoopParser.fromJson(jr));
				break;
			default:
				throw new IllegalArgumentException();
		}

		JsonObject stateMap = jo.getJsonObject("state_map");
		if(stateMap != null) {
			stateMap.forEach((k, v) -> b.mapState(k, ((JsonString)v).getString()));
		}
		return b;
	}

	public static class JobQueryInfo {
		public final String jobId;
		public final Actuator.AgentStatus status;
		public final JsonObject attributes;

		JobQueryInfo(String jobId, Actuator.AgentStatus status, JsonObject attributes) {
			this.jobId = Objects.requireNonNull(jobId, "jobId");
			this.status = Objects.requireNonNull(status, "status");
			this.attributes = Objects.requireNonNull(attributes, "attributes");
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			JobQueryInfo that = (JobQueryInfo)o;
			return jobId.equals(that.jobId) &&
					status == that.status &&
					attributes.equals(that.attributes);
		}

		@Override
		public int hashCode() {
			return Objects.hash(jobId, status, attributes);
		}

		@Override
		public String toString() {
			return "JobQueryInfo{" +
					"jobId='" + jobId + '\'' +
					", status=" + status +
					", attributes=" + attributes +
					'}';
		}
	}

	public static class Response extends CommandResponse {
		public final Map<String, JobQueryInfo> jobInfo;

		Response(RemoteShell.CommandResult cr, Map<String, JobQueryInfo> jobInfo) {
			super(cr);
			this.jobInfo = Collections.unmodifiableMap(Objects.requireNonNull(jobInfo, "jobInfo"));
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			if(!super.equals(o)) return false;
			Response response = (Response)o;
			return jobInfo.equals(response.jobInfo);
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), jobInfo);
		}
	}

	public static class Builder extends JobCommand.Builder<QueryCommand.Builder> {
		private Map<String, Actuator.AgentStatus> stateMap;

		public Builder() {
			this.stateMap = new HashMap<>();
		}

		public Builder mapState(String key, Actuator.AgentStatus state) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(state, "state");
			stateMap.put(key, state);
			return this;
		}

		public Builder mapState(String key, String state) {
			return mapState(key, Actuator.AgentStatus.valueOf(state));
		}

		public QueryCommand build() {
			Map<String, Actuator.AgentStatus> m = stateMap;
			stateMap = new HashMap<>();
			return new QueryCommand(argv.stream().toArray(String[]::new), parser, appendJobIds, m);
		}
	}

}
