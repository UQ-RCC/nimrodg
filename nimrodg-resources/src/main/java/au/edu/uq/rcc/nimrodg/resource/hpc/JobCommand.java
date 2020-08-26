package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class JobCommand<T extends CommandResponse> extends Command<T> {
	public final boolean appendJobIds;

	protected JobCommand(String[] argv, CommandParser parser, boolean appendJobIds) {
		super(argv, parser);
		this.appendJobIds = appendJobIds;
	}

	@Override
	protected Stream<String> buildArgvStream(Map<String, Object> vars) {
		String[] jobIds = (String[])vars.get("jobids");
		if(appendJobIds) {
			return Stream.concat(super.buildArgvStream(vars), Arrays.stream(jobIds));
		} else {
			return super.buildArgvStream(vars);
		}
	}

	@Override
	protected Map<String, JsonValue> parsePayload(CommandParser parser, String payload, Map<String, Object> vars) {
		return parser.parse(payload, (String[])vars.get("jobids"));
	}

	public T execute(RemoteShell shell, String[] jobIds) throws IOException {
		Objects.requireNonNull(shell, "shell");
		Objects.requireNonNull(jobIds, "jobIds");

		if(jobIds.length == 0) {
			throw new IllegalArgumentException("jobIds cannot be empty");
		}

		return this.execute(shell, Map.of("jobids", jobIds));
	}

	@Override
	public JsonObjectBuilder toJson() {
		return super.toJson().add("append_jobids", appendJobIds);
	}

	protected static <T extends JobCommand.Builder<T>> T fromJson(T b, JsonObject jo) {
		return Command.fromJson(b, jo).appendJobIds(jo.getBoolean("append_jobids", false));
	}

	@SuppressWarnings("unchecked")
	protected static class Builder<T extends JobCommand.Builder<T>> extends Command.Builder<T> {
		protected boolean appendJobIds;

		protected Builder() {
			this.appendJobIds = false;
		}

		public T appendJobIds(boolean appendJobIds) {
			this.appendJobIds = appendJobIds;
			return (T)this;
		}
	}
}
