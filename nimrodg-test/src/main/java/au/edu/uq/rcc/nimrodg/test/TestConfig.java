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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.setup.SetupConfig;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class TestConfig implements SetupConfig {

	public final AgentProvider agentProvider;
	public final NimrodConfig nimrodConfig;

	public TestConfig(Path root) {
		this.agentProvider = new TestAgentProvider(root);
		this.nimrodConfig = new TestNimrodConfig(root);
	}

	@Override
	public String workDir() {
		return nimrodConfig.getWorkDir();
	}

	@Override
	public String storeDir() {
		return nimrodConfig.getRootStore();
	}

	@Override
	public AMQPConfig amqp() {
		return new AMQPConfig() {
			@Override
			public URI uri() {
				return nimrodConfig.getAmqpUri().uri;
			}

			@Override
			public String routingKey() {
				return nimrodConfig.getAmqpRoutingKey();
			}

			@Override
			public String certPath() {
				return nimrodConfig.getAmqpUri().certPath;
			}

			@Override
			public boolean noVerifyPeer() {
				return nimrodConfig.getAmqpUri().noVerifyPeer;
			}

			@Override
			public boolean noVerifyHost() {
				return nimrodConfig.getAmqpUri().noVerifyHost;
			}
		};
	}

	@Override
	public TransferConfig transfer() {
		return new TransferConfig() {
			@Override
			public URI uri() {
				return nimrodConfig.getTransferUri().uri;
			}

			@Override
			public String certPath() {
				return nimrodConfig.getTransferUri().certPath;
			}

			@Override
			public boolean noVerifyPeer() {
				return nimrodConfig.getTransferUri().noVerifyPeer;
			}

			@Override
			public boolean noVerifyHost() {
				return nimrodConfig.getTransferUri().noVerifyHost;
			}
		};
	}

	@Override
	public Map<String, String> agents() {
		return agentProvider.lookupAgents().values().stream()
				.collect(Collectors.toMap(AgentInfo::getPlatformString, AgentInfo::getPath));
	}

	private static class _MachinePair implements MachinePair {

		public final String system;
		public final String machine;

		public _MachinePair(String system, String machine) {
			this.system = system;
			this.machine = machine;
		}

		@Override
		public String system() {
			return system;
		}

		@Override
		public String machine() {
			return machine;
		}
	}

	@Override
	public Map<MachinePair, String> agentMappings() {
		return agentProvider.lookupAgents().values().stream()
				.flatMap(ai -> ai.posixMappings().stream()
						.map(e -> Map.entry(new _MachinePair(e.getKey(), e.getValue()), ai.getPlatformString())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Override
	public Map<String, String> resourceTypes() {
		return Map.of("dummy", DummyResourceType.class.getCanonicalName());
	}

	@Override
	public Map<String, String> properties() {
		return Map.of(
				"nimrod.sched.default.launch_penalty", "-10",
				"nimrod.sched.default.spawn_cap", "10",
				"nimrod.sched.default.job_buf_size", "1000",
				"nimrod.sched.default.job_buf_refill_threshold", "100",
				"nimrod.master.run_rescan_interval", "60"
		);
	}

}
