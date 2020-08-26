package au.edu.uq.rcc.nimrodg.resource.hpc;

import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.util.Map;

public interface CommandParser {
	/**
	 * Parse a string payload.
	 * @param payload The raw payload. May not be null.
	 * @param keys The list of expected top-level keys. May not be null.
	 *             This is only a hint, and may be ignored if it makes sense
	 *             for the implementation to do so.
	 * @return The parsed payload.
	 */
	Map<String, JsonValue> parse(String payload, String[] keys);

	/* TODO: Make this package private */
	JsonObjectBuilder toJson();
}
