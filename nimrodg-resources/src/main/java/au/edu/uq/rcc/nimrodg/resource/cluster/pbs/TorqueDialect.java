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

import au.edu.uq.rcc.nimrodg.resource.cluster.BatchDialect;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

public class TorqueDialect implements BatchDialect {

	@Override
	public String[] buildSubmissionArguments(int batchSize, JsonObject[] batchConfig, String[] extra) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

//	@Override
//	public String[] generateArguments(int batchSize, AgentRequirements reqs) {
//		long hours = reqs.walltime / 3600;
//		long minutes = (reqs.walltime % 3600) / 60;
//		long seconds = reqs.walltime % 60;
//
//		return new String[]{
//			"-l", String.format("walltime=%d:%02d:%02d", hours, minutes, seconds),
//			"-l", String.format("nodes=1:ppn=%d:mem=%db", reqs.ncpus * batchSize, reqs.memory * batchSize)
//		};
//	}
	@Override
	public boolean parseResources(Resource[] scale, Resource[] static_, PrintStream out, PrintStream err, JsonArrayBuilder jb) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean validateResource(JsonObject res, List<String> errors) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Optional<Long> getWalltime(JsonObject[] batchConfig) {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
