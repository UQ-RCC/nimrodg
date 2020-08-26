package au.edu.uq.rcc.nimrodg.resource.hpc;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LineRegexParser implements CommandParser {
	/* Match named capture groups, as there's no supported way in Java to do it. */
	private static final Pattern NAMED_PATTERN = Pattern.compile("\\?<[a-zA-Z][a-zA-z0-9]*>");

	public final Pattern pattern;
	public final String joinKey;
	public final int limit;
	public final Set<String> namedGroups;

	@SuppressWarnings({"unchecked", "unused"})
	private static Stream<String> getNamedGroupsHacky(Pattern pattern) {
		Objects.requireNonNull(pattern, "pattern");
		/* XXX: There's no way in Java to get the named groups. Reflect on this until there is... */
		try {
			Method m = pattern.getClass().getDeclaredMethod("namedGroups");
			m.setAccessible(true);
			return ((Map<String, Integer>)m.invoke(pattern)).keySet().stream();
		} catch(ReflectiveOperationException e) {
			return Stream.empty();
		}
	}

	private static Stream<String> getNamedGroups(Pattern pattern) {
		Objects.requireNonNull(pattern, "pattern");
		return NAMED_PATTERN.matcher(pattern.pattern())
				.results()
				.map(MatchResult::group)
				.map(s -> s.substring(2, s.length() - 1)); /* Chop off the ?< and > */
	}

	LineRegexParser(Pattern pattern, String joinKey, int limit) {
		this.pattern = Objects.requireNonNull(pattern, "pattern");
		this.joinKey = Objects.requireNonNull(joinKey, "joinField");
		this.limit = limit < 1 ? Integer.MAX_VALUE : limit;
		this.namedGroups = getNamedGroups(pattern).collect(Collectors.toUnmodifiableSet());

		if(!this.namedGroups.contains(this.joinKey)) {
			throw new IllegalArgumentException("joinKey " + joinKey + " not in capture groups");
		}
	}

	public JsonArray parseToArray(String payload) {
		JsonArrayBuilder jab = Json.createArrayBuilder();

		for(String line : payload.split("\\r?\n")) {
			Matcher m = pattern.matcher(line);

			if(!m.matches()) {
				continue;
			}

			/* Build the attributes. */
			JsonObjectBuilder attrb = Json.createObjectBuilder();
			namedGroups.forEach(s -> {
				try {
					attrb.add(s, m.group(s));
				} catch(IllegalArgumentException e) {
					attrb.add(s, JsonValue.NULL);
				}
			});
			jab.add(attrb);
		}

		return jab.build();
	}

	@Override
	public JsonObject parse(String payload, String[] keys) {
		JsonObjectBuilder job = Json.createObjectBuilder();
		parseToArray(payload).stream()
				.map(JsonValue::asJsonObject)
				.filter(jo -> jo.containsKey(joinKey))
				.limit(limit)
				.forEach(jo -> job.add(jo.getString(joinKey), jo));
		return job.build();
	}

	@Override
	public JsonObjectBuilder toJson() {
		return Json.createObjectBuilder()
				.add("type", "line_regex")
				.add("regex", pattern.pattern());
	}

	static LineRegexParser fromJson(JsonObject jo, String joinKey, int limit) {
		Objects.requireNonNull(jo, "jo");
		Objects.requireNonNull(joinKey, "joinKey");
		return new LineRegexParser(Pattern.compile(jo.getString("regex")), joinKey, limit);
	}
}
