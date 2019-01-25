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
package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import java.io.IOException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.cluster.BatchedClusterResourceType;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import javax.json.JsonArray;
import javax.json.JsonString;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.cluster.slurm.SLURMActuator;
import au.edu.uq.rcc.nimrodg.resource.cluster.slurm.SLURMDialect;

public final class SLURMResourceType extends BatchedClusterResourceType {

	public SLURMResourceType() {
		super("slurm", "SLURM", "slurmargs", new SLURMDialect());
	}

	@Override
	protected boolean validateSubmissionArgs(JsonArray ja, List<String> errors) {
		boolean valid = super.validateSubmissionArgs(ja, errors);

		String[] slurmargs = ja.stream().map(v -> ((JsonString)v).getString()).toArray(String[]::new);

		long oeCount = Arrays.stream(slurmargs).filter(
				a -> a.equals("-o") || a.equals("-e") || a.startsWith("--error") || a.startsWith("--output")
		).count();
		return oeCount == 0 && valid;
	}

	@Override
	protected String getConfigSchema() {
		return "resource_slurm.json";
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, BatchedClusterConfig cfg) throws IOException {
		return new SLURMActuator(ops, node, amqpUri, certs, cfg);
	}
}
