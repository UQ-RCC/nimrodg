package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import com.hubspot.jinjava.Jinjava;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class Command<T extends CommandResponse> {
	public final List<String> argv;
	protected final CommandParser parser;

	protected Command(String[] argv, CommandParser parser) {
		this.argv = List.of(Objects.requireNonNull(argv, "argv"));
		this.parser = Objects.requireNonNull(parser, "parser");
	}

	/**
	 * Build the command line.
	 *
	 * This is the same as argv in C. The first element should be the program name.
	 *
	 * @param vars The map of template variables. This is immutable.
	 * @return A stream that when evaluated produces the list of arguments.
	 */
	protected Stream<String> buildArgvStream(Map<String, Object> vars) {
		return argv.stream().map(i -> HPCActuator.renderTemplate(i, vars));
	}

	protected abstract T processPayload(RemoteShell.CommandResult cr, Map<String, Object> vars, Map<String, JsonValue> payload);

	protected abstract Map<String, JsonValue> parsePayload(CommandParser parser, String payload, Map<String, Object> vars);

	protected T execute(RemoteShell shell, Map<String, Object> vars) throws IOException {
		RemoteShell.CommandResult cr = shell.runCommand(this.buildArgvStream(vars).toArray(String[]::new));

		if(cr.status != 0) {
			throw new IOException("nonzero");
		}

		return this.processPayload(cr, vars, this.parsePayload(parser, cr.stdout, vars));
	}

	@SuppressWarnings("unchecked")
	protected static abstract class Builder<T extends Builder<T>> {
		protected final List<String> argv;
		protected CommandParser parser;

		public Builder() {
			this.argv = new ArrayList<>();
		}

		public T argv(String... argv) {
			this.argv.clear();
			this.argv.addAll(List.of(argv));
			return (T)this;
		}

		public T parser(CommandParser parser) {
			this.parser = parser;
			return (T)this;
		}
	}

	protected static <T extends Command.Builder<T>> T fromJson(T b, JsonObject jo) {
		return b.argv(jo.getJsonArray("argv").stream()
				.map(jv -> ((JsonString)jv).getString())
				.toArray(String[]::new));
	}

	public JsonObjectBuilder toJson() {
		return Json.createObjectBuilder()
				.add("argv", Json.createArrayBuilder(argv))
				.add("response", parser.toJson());
	}
}
