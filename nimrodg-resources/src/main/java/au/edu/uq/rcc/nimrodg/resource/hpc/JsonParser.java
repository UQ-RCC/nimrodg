package au.edu.uq.rcc.nimrodg.resource.hpc;

import com.hubspot.jinjava.Jinjava;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

class JsonParser implements CommandParser {

	public final String varName;
	public final Map<String, String> attributes;

	JsonParser(String varName, Map<String, String> attributes) {
		this.varName = Objects.requireNonNull(varName, "varName");
		this.attributes = Collections.unmodifiableMap(Objects.requireNonNull(attributes, "attributes"));
	}

	@Override
	public JsonObject parse(String payload, String[] keys) {
		Objects.requireNonNull(payload, "payload");
		Objects.requireNonNull(keys, "keys");

		JsonStructure jp;
		try(StringReader is = new StringReader(payload)) {
			jp = Json.createReader(is).read();
		}

		JsonObjectBuilder job = Json.createObjectBuilder();
		for(String key : keys) {
			Map<String, ?> vars = Map.of(varName, key);

			JsonObjectBuilder attrb = Json.createObjectBuilder();

			attributes.forEach((k, v) -> {
				try {
					attrb.add(k, Json.createPointer(HPCActuator.renderTemplate(v, vars)).getValue(jp));
				} catch(JsonException e) {
					/* Will happen if either the key doesn't exist, or the pointer is invalid. */
					attrb.add(k, JsonValue.NULL);
				}
			});

			job.add(key, attrb);
		}

		return job.build();
	}

	@Override
	public JsonObjectBuilder toJson() {
		JsonObjectBuilder attrs = Json.createObjectBuilder();
		attributes.forEach(attrs::add);
		return Json.createObjectBuilder()
				.add("type", "json")
				.add("attributes", attrs);
	}

	static JsonParser fromJson(JsonObject jo, String varName) {
		Objects.requireNonNull(jo, "jo");
		Objects.requireNonNull(varName, "varName");

		return new JsonParser(varName, jo.getJsonObject("attributes").entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> ((JsonString)e.getValue()).getString())));
	}
}
