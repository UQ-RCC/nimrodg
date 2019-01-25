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
		return new HashMap<String, String>() {
			{
				put("x86_64-pc-linux-musl", path.resolve(".agents").resolve("x86_64-pc-linux-musl").toString());
				put("i686-pc-linux-musl", path.resolve(".agents").resolve("i686-pc-linux-musl").toString());
			}
		};
	}

	@Override
	public Map<MachinePair, String> agentMappings() {
		return new HashMap<MachinePair, String>() {
			{
				put(new MachinePair() {
					@Override
					public String system() {
						return "Linux";
					}

					@Override
					public String machine() {
						return "x86_64";
					}
				}, "x86_64-pc-linux-musl");

				put(new MachinePair() {
					@Override
					public String system() {
						return "Linux";
					}

					@Override
					public String machine() {
						return "k10m";
					}
				}, "x86_64-pc-linux-musl");

				put(new MachinePair() {
					@Override
					public String system() {
						return "Linux";
					}

					@Override
					public String machine() {
						return "i686";
					}
				}, "i686-pc-linux-musl");
			}
		};
	}

	@Override
	public Map<String, String> resourceTypes() {
		return new HashMap<String, String>() {
			{
				put("dummy", DummyResourceType.class.getCanonicalName());
			}
		};
	}

	@Override
	public Map<String, String> properties() {
		return new HashMap<String, String>() {
			{
				put("nimrod.sched.default.launch_penalty", "-10");
				put("nimrod.sched.default.spawn_cap", "10");
				put("nimrod.sched.default.job_buf_size", "1000");
				put("nimrod.sched.default.job_buf_refill_threshold", "100");
				put("nimrod.master.run_rescan_interval", "60");
			}
		};
	}

}
