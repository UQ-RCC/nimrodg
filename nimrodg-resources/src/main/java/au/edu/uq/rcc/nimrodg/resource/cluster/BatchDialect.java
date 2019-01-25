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

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

/**
 * Provides support for converting a resource list into a submission string.
 */
public interface BatchDialect {

	class Resource {

		public final String name;
		public final String value;

		public Resource(String name, String value) {
			this.name = name;
			this.value = value;
		}

	}

	/**
	 * Parse a list of batch resources into JSON.
	 *
	 * @param scale The list of scaling resources.
	 * @param static_ The list of static resources.
	 * @param out stdout
	 * @param err stderr
	 * @param jb
	 * @return
	 */
	boolean parseResources(Resource[] scale, Resource[] static_, PrintStream out, PrintStream err, JsonArrayBuilder jb);

	/**
	 * Given a batch resource JSON object, validate it.
	 *
	 * @param res The batch resource.
	 * @param errors A list where any error strings should be written to.
	 * @return If the resource is valid, returns true. Otherwise, false.
	 */
	boolean validateResource(JsonObject res, List<String> errors);

	/**
	 * Generate the submission arguments for a batch of batchSize agents.
	 *
	 * @param batchSize The size of the batch.
	 * @param batchConfig A list of batch resources. This is already validated.
	 * @param extra An array of extra submission arguments that should be added unconditionally.
	 * @return
	 */
	String[] buildSubmissionArguments(int batchSize, JsonObject[] batchConfig, String[] extra);

	/**
	 * Get the wall time in seconds.
	 *
	 * @param batchConfig list of batch resources. This is already validated.
	 * @return The wall time in seconds, or null if none given.
	 */
	Optional<Long> getWalltime(JsonObject[] batchConfig);
}
