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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

public final class IniSetupConfig implements SetupConfig {

	public final String workDir;
	public final String storeDir;

	public final URI amqpUri;
	public final String amqpRoutingKey;
	public final String amqpCertPath;
	public final boolean amqpNoVerifyPeer;
	public final boolean amqpNoVerifyHost;

	public final URI txUri;
	public final String txCertPath;
	public final boolean txNoVerifyPeer;
	public final boolean txNoVerifyHost;

	public final Map<String, String> agents;
	public final Map<MachinePair, String> agentMappings;
	public final Map<String, String> resourceTypes;
	public final Map<String, String> properties;

	public final AMQPConfig amqp;
	public final TransferConfig transfer;

	private final Map<MachinePair, String> actualAgentMappings;
	private final Map<String, String> actualResourceTypes;
	private final Map<String, String> actualProperties;

	public IniSetupConfig(Ini ini) {
		Map<String, String> envMap = IniUserConfig.buildEnvironmentMap();

		Section _cfg = IniUserConfig.requireSection(ini, "config");
		_cfg.putAll(envMap);

		String _workDir = IniUserConfig.requireValue(_cfg, "workdir");
		if(!_workDir.endsWith("/")) {
			_workDir += "/";
		}
		this.workDir = _workDir;

		String _storeDir = IniUserConfig.requireValue(_cfg, "storedir");
		if(!_storeDir.endsWith("/")) {
			_storeDir += "/";
		}
		this.storeDir = _storeDir;

		Section _amqp = IniUserConfig.requireSection(ini, "amqp");
		_amqp.putAll(envMap);
		this.amqpUri = URI.create(IniUserConfig.requireValue(_amqp, "uri"));
		this.amqpRoutingKey = IniUserConfig.requireValue(_amqp, "routing_key");
		this.amqpCertPath = IniUserConfig.requireValue(_amqp, "cert");
		this.amqpNoVerifyPeer = Boolean.parseBoolean(IniUserConfig.requireValue(_amqp, "no_verify_peer"));
		this.amqpNoVerifyHost = Boolean.parseBoolean(IniUserConfig.requireValue(_amqp, "no_verify_host"));

		Section _tx = IniUserConfig.requireSection(ini, "transfer");
		_tx.putAll(envMap);
		{
			String uri = IniUserConfig.requireValue(_tx, "uri");
			if(!uri.endsWith("/")) {
				uri += "/";
			}
			this.txUri = URI.create(uri);
		}
		this.txCertPath = IniUserConfig.requireValue(_tx, "cert");
		this.txNoVerifyPeer = Boolean.parseBoolean(IniUserConfig.requireValue(_tx, "no_verify_peer"));
		this.txNoVerifyHost = Boolean.parseBoolean(IniUserConfig.requireValue(_tx, "no_verify_host"));

		Section as = IniUserConfig.requireSection(ini, "agents");
		this.agents = as.keySet().stream().collect(Collectors.toMap(k -> k, k -> as.fetch(k)));

		Pattern p = Pattern.compile("^([^,]+),([^,]+)$");
		Section _agentmap = IniUserConfig.requireSection(ini, "agentmap");
		this.actualAgentMappings = _agentmap.entrySet().stream()
				.collect(Collectors.toMap(
						e -> {
							Matcher m = p.matcher(e.getKey());
							if(!m.matches()) {
								throw new IllegalArgumentException("Invalid agent mapping");
							}

							return new _MachinePair(m.group(1), m.group(2));
						},
						e -> e.getValue()
				));
		this.agentMappings = Collections.unmodifiableMap(actualAgentMappings);

		this.actualResourceTypes = new HashMap<>(IniUserConfig.requireSection(ini, "resource_types"));
		this.resourceTypes = Collections.unmodifiableMap(actualResourceTypes);

		this.actualProperties = new HashMap<>(IniUserConfig.requireSection(ini, "properties"));
		this.properties = Collections.unmodifiableMap(actualProperties);

		this.amqp = new AMQPConfig() {
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
				return amqpCertPath;
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

		this.transfer = new TransferConfig() {
			@Override
			public URI uri() {
				return txUri;
			}

			@Override
			public String certPath() {
				return txCertPath;
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
	public String workDir() {
		return workDir;
	}

	@Override
	public String storeDir() {
		return storeDir;
	}

	@Override
	public AMQPConfig amqp() {
		return amqp;
	}

	@Override
	public TransferConfig transfer() {
		return transfer;
	}

	@Override
	public Map<String, String> agents() {
		return agents;
	}

	@Override
	public Map<MachinePair, String> agentMappings() {
		return agentMappings;
	}

	@Override
	public Map<String, String> resourceTypes() {
		return resourceTypes;
	}

	@Override
	public Map<String, String> properties() {
		return properties;
	}

	private class _MachinePair implements MachinePair {

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

		@Override
		public String toString() {
			return String.format("%s,%s", system, machine);
		}

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 53 * hash + Objects.hashCode(this.system);
			hash = 53 * hash + Objects.hashCode(this.machine);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null) {
				return false;
			}
			if(getClass() != obj.getClass()) {
				return false;
			}
			final _MachinePair other = (_MachinePair)obj;
			if(!Objects.equals(this.system, other.system)) {
				return false;
			}
			return Objects.equals(this.machine, other.machine);
		}

	}
}
