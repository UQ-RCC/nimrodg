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
package au.edu.uq.rcc.nimrodg.setup;

import au.edu.uq.rcc.nimrodg.api.MachinePair;

import java.util.Map;

public final class SetupConfig {

	private final String workDir;
	private final String storeDir;
	private final AMQPConfig amqp;
	private final TransferConfig tx;
	private final Map<String, String> agents;
	private final Map<MachinePair, String> agentMappings;
	private final Map<String, String> resourceTypes;
	private final Map<String, String> properties;

	SetupConfig(String workDir, String storeDir, AMQPConfig amqp, TransferConfig tx, Map<String, String> agents, Map<MachinePair, String> agentMappings, Map<String, String> resourceTypes, Map<String, String> properties) {
		this.workDir = workDir;
		this.storeDir = storeDir;
		this.amqp = amqp;
		this.tx = tx;
		this.agents = Map.copyOf(agents);
		this.agentMappings = Map.copyOf(agentMappings);
		this.resourceTypes = Map.copyOf(resourceTypes);
		this.properties = Map.copyOf(properties);
	}

	public String workDir() {
		return this.workDir;
	}

	public String storeDir() {
		return this.storeDir;
	}

	public AMQPConfig amqp() {
		return this.amqp;
	}

	public TransferConfig transfer() {
		return this.tx;
	}

	public Map<String, String> agents() {
		return this.agents;
	}

	public Map<MachinePair, String> agentMappings() {
		return this.agentMappings;
	}

	public Map<String, String> resourceTypes() {
		return this.resourceTypes;
	}

	public Map<String, String> properties() {
		return this.properties;
	}
}
