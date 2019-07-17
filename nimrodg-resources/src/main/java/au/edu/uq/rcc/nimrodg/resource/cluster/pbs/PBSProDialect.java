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
package au.edu.uq.rcc.nimrodg.resource.cluster.pbs;

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

public class PBSProDialect extends BaseDialect {

	private static final Map<String, BatchResource> PBS_RESOURCES = Map.of(
			"mem", new BatchResource("mem", true, "b", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger),
			"vmem", new BatchResource("vmem", true, "b", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger),
			"mpiprocs", new BatchResource("mpiprocs", true, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"ompthreads", new BatchResource("ompthreads", true, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"ncpus", new BatchResource("ncpus", true, "", BaseDialect::parseUnsignedLong, BaseDialect::validatePositiveInteger),
			"walltime", new BatchResource("walltime", false, "", BaseDialect::parseWalltime, BaseDialect::validatePositiveInteger),
			"pmem", new BatchResource("pmem", false, "b", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger),
			"pvmem", new BatchResource("pvmem", false, "b", BaseDialect::parseMemory, BaseDialect::validatePositiveInteger)
	);

	@Override
	protected Map<String, BatchResource> getBatchResources() {
		return PBS_RESOURCES;
	}

	private void handleResource(int batchSize, String name, boolean scale, JsonValue _value, List<String> select, List<String> standalone) {
		/* Process the value. */
		String value;
		if(scale) {
			value = Long.toString(batchSize * ((JsonNumber)_value).longValue(), 10);
		} else {
			value = _value.toString();
		}

		BatchResource res = PBS_RESOURCES.getOrDefault(name, null);
		if(res != null) {
			if(res.scale) {
				select.add(String.format("%s=%s%s", name, value, res.suffix));
			} else {
				standalone.add("-l");
				standalone.add(String.format("%s=%s%s", name, value, res.suffix));
			}
		} else {
			/* For now, assume that anything unknown is a chunk resource. */
			select.add(String.format("%s=%s", name, value));
		}
	}

	@Override
	public String[] buildSubmissionArguments(int batchSize, JsonObject[] batchConfig, String[] extra) {
		List<String> selectArgs = new ArrayList<>();
		selectArgs.add("select=1");

		List<String> standaloneArgs = new ArrayList<>();

		for(JsonObject jo : batchConfig) {
			handleResource(
					batchSize,
					jo.getString("name"),
					jo.getBoolean("scale"),
					jo.get("value"),
					selectArgs,
					standaloneArgs
			);
		}

		return Stream.concat(
				Arrays.stream(extra),
				Stream.concat(
						standaloneArgs.stream(),
						Stream.of("-l", String.join(":", selectArgs))
				)
		).toArray(String[]::new);
	}

	@Override
	public Optional<Long> getWalltime(JsonObject[] batchConfig) {
		return Arrays.stream(batchConfig)
				.filter(b -> b.getString("name").equals("walltime"))
				.findFirst()
				.map(j -> j.getJsonNumber("value").longValue());
	}
}
