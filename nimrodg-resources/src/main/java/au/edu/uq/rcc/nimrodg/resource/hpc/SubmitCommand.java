package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class SubmitCommand extends Command<SubmitCommand.Response> {

	private static final String[] EMPTY_KEYS = new String[0];

	private SubmitCommand(String[] argv, CommandParser parser) {
		super(argv, parser);
	}

	@Override
	protected Response processPayload(RemoteShell.CommandResult cr, Map<String, Object> vars, Map<String, JsonValue> payload) {
		return new Response(cr, payload.keySet().stream()
				.findFirst()
				.orElseThrow(IllegalArgumentException::new));
	}

	@Override
	protected Map<String, JsonValue> parsePayload(CommandParser parser, String payload, Map<String, Object> vars) {
		return parser.parse(payload, EMPTY_KEYS);
	}

	public Response execute(RemoteShell shell, String scriptPath) throws IOException {
		return this.execute(shell, Map.of("script_path", scriptPath));
	}

	public static class Response extends CommandResponse {
		public final String jobId;

		Response(RemoteShell.CommandResult cr, String jobId) {
			super(cr);
			this.jobId = Objects.requireNonNull(jobId, "jobId");
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			if(!super.equals(o)) return false;
			Response response = (Response)o;
			return jobId.equals(response.jobId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), jobId);
		}
	}

	public static Builder fromJson(JsonObject jo) {
		Objects.requireNonNull(jo, "jo");

		Builder b = new Builder();
		Command.fromJson(b, jo);

		JsonObject jr = jo.getJsonObject("response");
		switch(jr.getString("type")) {
			case "line_regex":
				b.parser(LineRegexParser.fromJson(jr, "jobid", 1));
				break;
			case "json":
				b.parser(JsonParser.fromJson(jr, "dummy"));
				break;
			case "none":
				b.parser(NoopParser.fromJson(jr));
				break;
			default:
				throw new IllegalArgumentException();
		}

		return b;
	}

	public static class Builder extends Command.Builder<SubmitCommand.Builder> {
		public SubmitCommand build() {
			return new SubmitCommand(argv.stream().toArray(String[]::new), parser);
		}
	}

}
