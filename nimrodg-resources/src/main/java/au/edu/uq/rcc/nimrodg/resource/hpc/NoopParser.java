package au.edu.uq.rcc.nimrodg.resource.hpc;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.util.Collections;
import java.util.Map;

public class NoopParser implements CommandParser {
	@Override
	public Map<String, JsonValue> parse(String payload, String[] keys) {
		return Collections.emptyMap();
	}

	@Override
	public JsonObjectBuilder toJson() {
		return Json.createObjectBuilder().add("type", "none");
	}

	static NoopParser fromJson(JsonObject jo) {
		return new NoopParser();
	}
}
