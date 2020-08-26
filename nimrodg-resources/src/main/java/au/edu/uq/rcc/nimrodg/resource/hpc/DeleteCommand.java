package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.Map;
import java.util.Objects;

public class DeleteCommand extends JobCommand<CommandResponse> {
	private DeleteCommand(String[] argv, CommandParser parser, boolean appendJobIds) {
		super(argv, parser, appendJobIds);
	}

	@Override
	protected CommandResponse processPayload(RemoteShell.CommandResult cr, Map<String, Object> vars, Map<String, JsonValue> payload) {
		return CommandResponse.empty(cr);
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

	public static class Builder extends JobCommand.Builder<DeleteCommand.Builder> {
		public DeleteCommand build() {
			return new DeleteCommand(argv.stream().toArray(String[]::new), parser, appendJobIds);
		}
	}
}
