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

import au.edu.uq.rcc.nimrodg.api.setup.AMQPConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.TransferConfigBuilder;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class IniSetupConfig {

	public static SetupConfigBuilder parseToBuilder(Ini ini, Optional<Path> userConfigPath) {
		SetupConfigBuilder builder = new SetupConfigBuilder();

		Map<String, String> envMap = IniUserConfig.buildEnvironmentMap();

		/* I'd rather have it a dummy invalid value than empty. */
		envMap.put("nimrod:confdir", userConfigPath.map(p -> p.getParent().toAbsolutePath().toString()).orElse("/nonexistent"));

		Section _cfg = IniUserConfig.requireSection(ini, "config");
		_cfg.putAll(envMap);

		String _workDir = IniUserConfig.requireValue(_cfg, "workdir");
		if(!_workDir.endsWith("/")) {
			_workDir += "/";
		}
		builder.workDir(_workDir);

		String _storeDir = IniUserConfig.requireValue(_cfg, "storedir");
		if(!_storeDir.endsWith("/")) {
			_storeDir += "/";
		}
		builder.storeDir(_storeDir);


		Section _amqp = IniUserConfig.requireSection(ini, "amqp");
		_amqp.putAll(envMap);

		builder.amqp(new AMQPConfigBuilder()
				.uri(URI.create(IniUserConfig.requireValue(_amqp, "uri")))
				.routingKey(IniUserConfig.requireValue(_amqp, "routing_key"))
				.certPath(IniUserConfig.requireValue(_amqp, "cert"))
				.noVerifyPeer(Boolean.parseBoolean(IniUserConfig.requireValue(_amqp, "no_verify_peer")))
				.noVerifyHost(Boolean.parseBoolean(IniUserConfig.requireValue(_amqp, "no_verify_host")))
				.build()
		);

		Section _tx = IniUserConfig.requireSection(ini, "transfer");
		_tx.putAll(envMap);

		builder.transfer(new TransferConfigBuilder()
				.uri(getTransferUri(IniUserConfig.requireValue(_tx, "uri")))
				.certPath(IniUserConfig.requireValue(_tx, "cert"))
				.noVerifyPeer(Boolean.parseBoolean(IniUserConfig.requireValue(_tx, "no_verify_peer")))
				.noVerifyHost(Boolean.parseBoolean(IniUserConfig.requireValue(_tx, "no_verify_host")))
				.build()
		);

		builder.agents(IniUserConfig.requireSection(ini, "agents").entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> Paths.get(e.getValue()))));

		Pattern p = Pattern.compile("^([^,]+),([^,]+)$");
		Section _agentmap = IniUserConfig.requireSection(ini, "agentmap");

		_agentmap.forEach((k, v) -> {
			Matcher m = p.matcher(k);
			if(!m.matches()) {
				throw new IllegalArgumentException("Invalid agent mapping");
			}

			builder.agentMapping(m.group(1), m.group(2), v);
		});

		builder.resourceTypes(IniUserConfig.requireSection(ini, "resource_types"));
		builder.properties(IniUserConfig.requireSection(ini, "properties"));

		return builder;
	}

	private static URI getTransferUri(String uri) {
		URI txUri = URI.create(uri);
		String path = txUri.getPath();
		if(path.endsWith("/")) {
			return txUri;
		}

		try {
			return new URI(
					txUri.getScheme(), txUri.getUserInfo(),
					txUri.getHost(), txUri.getPort(),
					txUri.getPath() + "/",
					txUri.getQuery(), txUri.getFragment()
			);
		} catch(URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
