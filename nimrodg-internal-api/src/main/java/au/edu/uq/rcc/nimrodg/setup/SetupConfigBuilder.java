/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
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

import au.edu.uq.rcc.nimrodg.api.ResourceType;

import java.util.HashMap;
import java.util.Map;

public class SetupConfigBuilder {

	private String workDir;
	private String storeDir;
	private AMQPConfig amqp;
	private TransferConfig transfer;
	private Map<String, String> agents;
	private Map<MachinePair, String> agentMappings;
	private Map<String, String> resourceTypes;
	private Map<String, String> properties;

	public SetupConfigBuilder() {
		this.agents = new HashMap<>();
		this.agentMappings = new HashMap<>();
		this.resourceTypes = new HashMap<>();
		this.properties = new HashMap<>();
	}

	public SetupConfigBuilder workDir(String workDir) {
		this.workDir = workDir;
		return this;
	}

	public SetupConfigBuilder storeDir(String storeDir) {
		this.storeDir = storeDir;
		return this;
	}

	public SetupConfigBuilder amqp(AMQPConfig amqp) {
		this.amqp = amqp;
		return this;
	}

	public SetupConfigBuilder transfer(TransferConfig tx) {
		this.transfer = tx;
		return this;
	}

	public SetupConfigBuilder agent(String platform, String path) {
		if(platform == null) {
			return this;
		}

		if(path == null) {
			agents.remove(platform);
		} else {
			agents.put(platform, path);
		}

		return this;
	}

	public SetupConfigBuilder agents(Map<String, String> agents) {
		if(agents == null) {
			return this;
		}

		this.agents.putAll(agents);

		return this;
	}

	public SetupConfigBuilder agentMapping(String system, String machine, String platform) {
		return agentMapping(MachinePair.of(system, machine), platform);
	}

	public SetupConfigBuilder agentMapping(MachinePair mp, String platform) {
		if(mp == null) {
			return this;
		}

		if(platform == null) {
			agentMappings.remove(mp);
		} else {
			agentMappings.put(mp, platform);
		}
		return this;
	}

	public SetupConfigBuilder resourceType(String name, String clazz) {
		if(name == null) {
			return this;
		}

		if(clazz == null) {
			resourceTypes.remove(name);
		} else {
			resourceTypes.put(name, clazz);
		}

		return this;
	}

	public SetupConfigBuilder resourceType(String name, Class<? extends ResourceType> clazz) {
		if(name == null) {
			return this;
		}

		if(clazz == null) {
			resourceTypes.remove(name);
		} else {
			resourceTypes.put(name, clazz.getCanonicalName());
		}

		return this;
	}

	public SetupConfigBuilder resourceTypes(Map<String, String> types) {
		if(types == null) {
			return this;
		}

		resourceTypes.putAll(types);
		return this;
	}

	public SetupConfigBuilder property(String key, String value) {
		if(key == null) {
			return this;
		}

		if(value == null) {
			properties.remove(key);
		} else {
			properties.put(key, value);
		}

		return this;
	}

	public SetupConfigBuilder property(String key, int value) {
		return property(key, String.valueOf(value));
	}

	public SetupConfigBuilder property(String key, boolean value) {
		return property(key, String.valueOf(value));
	}

	public SetupConfigBuilder property(String key, float value) {
		return property(key, String.valueOf(value));
	}

	public SetupConfigBuilder properties(Map<String, String> properties) {
		if(properties == null) {
			return this;
		}

		this.properties.putAll(properties);

		return this;
	}

	public void clear() {
		workDir = null;
		storeDir = null;
		agents.clear();
		agentMappings.clear();
		resourceTypes.clear();
		properties.clear();
	}

	public SetupConfig build() {
		if(workDir == null || storeDir == null || amqp == null || transfer == null) {
			throw new IllegalArgumentException();
		}

		if(!workDir.endsWith("/")) {
			workDir += "/";
		}

		if(!storeDir.endsWith("/")) {
			storeDir += "/";
		}

		SetupConfig cfg = new SetupConfig(workDir, storeDir, amqp, transfer, agents, agentMappings, resourceTypes, properties);

		clear();
		return cfg;
	}
}
