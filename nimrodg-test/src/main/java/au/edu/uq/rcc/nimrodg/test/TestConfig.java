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

import au.edu.uq.rcc.nimrodg.setup.SetupConfig;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TestConfig implements SetupConfig {

	public final Path path;

	public TestConfig(Path root) {
		this.path = root;
	}

	@Override
	public String workDir() {
		return path.toString();
	}

	@Override
	public String storeDir() {
		return path.resolve("experiments/").toString();
	}

	@Override
	public AMQPConfig amqp() {
		return new AMQPConfig() {
			@Override
			public URI uri() {
				return URI.create("amqps://user:pass@hostname/vhost");
			}

			@Override
			public String routingKey() {
				return "iamthemaster";
			}

			@Override
			public String certPath() {
				return "";
			}

			@Override
			public boolean noVerifyPeer() {
				return false;
			}

			@Override
			public boolean noVerifyHost() {
				return false;
			}
		};
	}

	@Override
	public TransferConfig transfer() {
		return new TransferConfig() {
			@Override
			public URI uri() {
				return URI.create("http://localhost:8080/storage/");
			}

			@Override
			public String certPath() {
				return "";
			}

			@Override
			public boolean noVerifyPeer() {
				return false;
			}

			@Override
			public boolean noVerifyHost() {
				return false;
			}
		};
	}

	@Override
	public Map<String, String> agents() {
		return Map.of(
				"x86_64-pc-linux-musl", path.resolve(".agents").resolve("x86_64-pc-linux-musl").toString(),
				"i686-pc-linux-musl", path.resolve(".agents").resolve("i686-pc-linux-musl").toString(),
				"noop", "/bin/true"
		);
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
		return Map.of(
				new _MachinePair("Linux", "x86_64"), "x86_64-pc-linux-musl",
				new _MachinePair("Linux", "k10m"), "x86_64-pc-linux-musl",
				new _MachinePair("Linux", "i686"), "i686-pc-linux-musl"
		);
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
