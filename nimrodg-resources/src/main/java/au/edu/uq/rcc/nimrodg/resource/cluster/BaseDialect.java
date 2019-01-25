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
package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;

/**
 * A convenience class for implementing batch dialects.
 * Handles all the tedious validation.
 */
public abstract class BaseDialect implements BatchDialect {

	@FunctionalInterface
	protected static interface Validator {

		boolean validate(JsonObject res, String name, List<String> errors);
	}

	@FunctionalInterface
	protected static interface Parser extends Function<String, JsonValue> {
	}

	@FunctionalInterface
	protected static interface Applier {
		long apply(long value);
	}

	protected static class BatchResource {

		public final String name;
		public final boolean scale;
		public final String suffix;
		public final Parser parser;
		public final Validator validator;

		public BatchResource(String name, boolean scale, String suffix, Parser parser, Validator validator) {
			this.name = name;
			this.scale = scale;
			this.suffix = suffix;
			this.parser = parser;
			this.validator = validator;
		}

		@Override
		public String toString() {
			return String.format("{%s, %s, %s},", name, scale ? "scales" : "static", suffix);
		}
	}

	protected abstract Map<String, BatchResource> getBatchResources();

	@Override
	public boolean parseResources(Resource[] scale, Resource[] static_, PrintStream out, PrintStream err, JsonArrayBuilder jb) {
		boolean valid = true;

		for(BatchDialect.Resource res : static_) {
			valid = parseResource(res, err, false, jb) && valid;
		}

		for(BatchDialect.Resource res : scale) {
			valid = parseResource(res, err, true, jb) && valid;
		}

		return valid;
	}

	private boolean parseResource(BatchDialect.Resource res, PrintStream err, boolean scale, JsonArrayBuilder jb) {
		JsonValue value;
		try {
			BatchResource bres = getBatchResources().getOrDefault(res.name, null);
			if(bres == null) {
				value = Json.createValue(res.value);
			} else if(!bres.scale && scale) {
				err.printf("Resource '%s' cannot scale.\n", res.name);
				return false;
			} else {
				value = bres.parser.apply(res.value);
			}
		} catch(NumberFormatException e) {
			err.printf("Malformed resource value for '%s', must be an unsigned integer.\n", res.name);
			return false;
		} catch(IllegalArgumentException e) {
			e.printStackTrace(err);
			return false;
		}

		jb.add(Json.createObjectBuilder()
				.add("name", res.name)
				.add("value", value)
				.add("scale", scale ? JsonValue.TRUE : JsonValue.FALSE)
		);
		return true;
	}

	@Override
	public boolean validateResource(JsonObject res, List<String> errors) {
		if(!res.keySet().equals(Set.of("name", "value", "scale"))) {
			errors.add("Batch resource must only contain 'name', 'value', and 'scale' fields.");
			return false;
		}

		if(!validateStringField(res, "name", errors)) {
			return false;
		}

		String name = res.getString("name");

		boolean valid;
		BatchResource pres = getBatchResources().getOrDefault(name, null);
		if(pres == null) {
			valid = validateStringField(res, "value", errors);
		} else {
			valid = pres.validator.validate(res, "value", errors);
		}

		JsonValue scale = res.get("scale");
		if(scale.getValueType() != JsonValue.ValueType.FALSE && scale.getValueType() != JsonValue.ValueType.TRUE) {
			errors.add("Batch resource element 'scale' is not a boolean.");
			return false;
		}
		return valid;
	}

	@Override
	public abstract String[] buildSubmissionArguments(int batchSize, JsonObject[] batchConfig, String[] extra);

	@Override
	public abstract Optional<Long> getWalltime(JsonObject[] batchConfig);

	protected static JsonValue parseMemory(String value) {
		return Json.createValue(StringUtils.parseMemory(value));
	}

	protected static JsonValue parseWalltime(String value) {
		return Json.createValue(StringUtils.parseWalltime(value));
	}

	protected static JsonValue parseUnsignedLong(String value) {
		return Json.createValue(Long.parseUnsignedLong(value, 10));
	}

	protected static boolean validateStringField(JsonObject res, String name, List<String> errors) {
		JsonValue value = res.get(name);
		if(value == null) {
			errors.add(String.format("Missing element '%s' for batch resource.", name));
			return false;
		}

		if(value.getValueType() != JsonValue.ValueType.STRING) {
			errors.add(String.format("Invalid type for batch resource '%s'.", name));
			return false;
		}
		return true;
	}

	protected static boolean validatePositiveInteger(JsonObject res, String name, List<String> errors) {
		JsonValue val = res.get(name);
		if(val == null) {
			errors.add(String.format("Missing element '%s' for batch resource.", name));
			return false;
		}

		if(val.getValueType() != JsonValue.ValueType.NUMBER) {
			errors.add(String.format("Batch resource element '%s' is not an integer.", name));
			return false;
		} else {
			int limit = ((JsonNumber)val).intValue();
			if(limit <= 0) {
				errors.add(String.format("Batch resource element '%s' must be > 0.", name));
				return false;
			}
		}

		return true;
	}
}
