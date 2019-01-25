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
package au.edu.uq.rcc.nimrodg.cli;

import au.edu.uq.rcc.nimrodg.setup.SetupConfig;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import net.sourceforge.argparse4j.inf.Namespace;

public class ArgumentsSetupConfig implements SetupConfig {

	public final String workDir;
	public final String storeDir;
	public final URI amqpUri;
	public final String amqpRoutingKey;
	public final String amqpCert;
	public final boolean amqpNoVerifyPeer;
	public final boolean amqpNoVerifyHost;

	public final URI txUri;
	public final String txCert;
	public final boolean txNoVerifyPeer;
	public final boolean txNoVerifyHost;

	private static boolean cb(Boolean b) {
		if(b == null) {
			return false;
		}

		return b;
	}

	public ArgumentsSetupConfig(Namespace args) {
		this.workDir = args.getString("workdir");
		this.storeDir = args.getString("storedir");
		this.amqpUri = URI.create(args.getString("amqp_uri"));
		this.amqpRoutingKey = args.getString("amqp_routing_key");
		this.amqpCert = args.getString("amqp_cert");

		this.amqpNoVerifyPeer = cb(args.getBoolean("amqp_no_verify_peer"));
		this.amqpNoVerifyHost = cb(args.getBoolean("amqp_no_verify_host"));

		this.txUri = URI.create(args.getString("tx_uri"));
		this.txCert = args.getString("tx_cert");
		this.txNoVerifyPeer = cb(args.getBoolean("tx_no_verify_peer"));
		this.txNoVerifyHost = cb(args.getBoolean("tx_no_verify_host"));
	}

	@Override
	public String workDir() {
		return workDir;
	}

	@Override
	public String storeDir() {
		return storeDir;
	}

	@Override
	public AMQPConfig amqp() {
		return new AMQPConfig() {
			@Override
			public URI uri() {
				return amqpUri;
			}

			@Override
			public String routingKey() {
				return amqpRoutingKey;
			}

			@Override
			public String certPath() {
				return amqpCert;
			}

			@Override
			public boolean noVerifyPeer() {
				return amqpNoVerifyPeer;
			}

			@Override
			public boolean noVerifyHost() {
				return amqpNoVerifyHost;
			}
		};
	}

	@Override
	public TransferConfig transfer() {
		return new TransferConfig() {
			@Override
			public URI uri() {
				return txUri;
			}

			@Override
			public String certPath() {
				return txCert;
			}

			@Override
			public boolean noVerifyPeer() {
				return txNoVerifyPeer;
			}

			@Override
			public boolean noVerifyHost() {
				return txNoVerifyHost;
			}
		};
	}

	@Override
	public Map<String, String> agents() {
		return Map.of();
	}

	@Override
	public Map<MachinePair, String> agentMappings() {
		return Map.of();
	}

	@Override
	public Map<String, String> resourceTypes() {
		return Map.of();
	}

	@Override
	public Map<String, String> properties() {
		return Map.of();
	}

}
