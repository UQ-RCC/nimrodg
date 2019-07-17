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
package au.edu.uq.rcc.nimrodg.resource.cluster.slurm;

import au.edu.uq.rcc.nimrodg.resource.cluster.BaseDialect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;

public class SLURMDialect extends BaseDialect {

	private static final Map<String, BatchResource> SLURM_RESOURCES = Map.of(
			"cpus-per-task", new BatchResource("cpus-per-task", false, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"nodes", new BatchResource("nodes", false, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"ntasks", new BatchResource("ntasks", true, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"ntasks-per-node", new BatchResource("ntasks-per-node", false, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			/* These guys need to be converted into kilobytes. */
			"mem-per-cpu", new BatchResource("mem-per-cpu", false, "K", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger),
			"mem", new BatchResource("mem", false, "K", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger)
	);

	@Override
	protected Map<String, BatchResource> getBatchResources() {
		return SLURM_RESOURCES;
	}

	private void handleResource(int batchSize, String name, boolean scale, JsonValue _value, List<String> args) {
		/* Process the value. */
		String value = patchValue(name, _value, scale ? Optional.of(batchSize) : Optional.empty());

		args.add(String.format("--%s", name));

		BatchResource res = SLURM_RESOURCES.getOrDefault(name, null);
		if(res != null) {
			args.add(String.format("%s%s", value, res.suffix));
		} else {
			/* Unknown resource, assume the user knows what they're doing. */
			args.add(value);
		}
	}

	private static String patchValue(String name, JsonValue _value, Optional<Integer> batchSize) {
		/* SLURM needs these in kilobytes. */
		if("mem".equals(name) || "mem-per-cpu".equals(name)) {
			return Long.toString(batchSize.orElse(1) * ((JsonNumber)_value).longValue() / 1000, 10);
		}

		if(!batchSize.isPresent()) {
			return _value.toString();
		}

		return Long.toString(batchSize.get() * ((JsonNumber)_value).longValue(), 10);
	}

	@Override
	public String[] buildSubmissionArguments(int batchSize, JsonObject[] batchConfig, String[] extra) {
		List<String> args = new ArrayList<>();

		for(JsonObject jo : batchConfig) {
			handleResource(
					batchSize,
					jo.getString("name"),
					jo.getBoolean("scale"),
					jo.get("value"),
					args
			);
		}

		return Stream.concat(args.stream(), Arrays.stream(extra)).toArray(String[]::new);
	}

	@Override
	public Optional<Long> getWalltime(JsonObject[] batchConfig) {
		return Arrays.stream(batchConfig)
				.filter(b -> b.getString("name").equals("time"))
				.findFirst()
				.map(j -> j.getJsonNumber("value").longValue());
	}
}
