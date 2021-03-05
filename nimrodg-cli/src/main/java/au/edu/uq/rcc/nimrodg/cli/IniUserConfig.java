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

import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

public class IniUserConfig implements UserConfig {

	public final String factory;
	public final Map<String, Map<String, String>> config;
	public final Path configPath;

	private final Map<String, Map<String, String>> actualConfig;

	public IniUserConfig(Ini ini, Path configPath) {
		Section _cfg = IniUserConfig.requireSection(ini, "config");

		/* Patch the entire ini with our "env:" variables. */
		Map<String, String> envMap = buildEnvironmentMap();
		envMap.put("nimrod:confdir", configPath.getParent().toAbsolutePath().toString());

		patchIni(ini, envMap);

		this.factory = requireValue(_cfg, "factory");

		/* "Resolve" the entire ini file and strip any "env:" entries. */
		this.actualConfig = ini.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						e -> e.getValue().keySet().stream()
								.filter(k -> !k.startsWith("env:") && !k.startsWith("nimrod:"))
								.collect(Collectors.toMap(k -> k, v -> e.getValue().fetch(v))))
				);

		this.config = Collections.unmodifiableMap(actualConfig);
		this.configPath = configPath;

	}

	public static Section requireSection(Ini ini, String name) {
		Section s = ini.get(name);
		if(s == null) {
			throw new IllegalArgumentException(String.format("Missing [%s] section", name));
		}
		return s;
	}

	public static String requireValue(Section s, String name) {
		String val = s.fetch(name);
		if(val == null) {
			throw new IllegalArgumentException(String.format("Missing key '%s' in [%s] section", name, s.getName()));
		}
		return val;
	}

	public static Map<String, String> buildEnvironmentMap() {
		return System.getenv().entrySet().stream()
				.collect(Collectors.toMap(e -> "env:" + e.getKey(), Map.Entry::getValue));
	}

	private static void _patch(Section s, Map<String, String> envMap) {
		s.putAll(envMap);
		for(String ss : s.childrenNames()) {
			_patch(s.getChild(ss), envMap);
		}
	}

	public static void patchIni(Ini ini, Map<String, String> patch) {
		ini.keySet().forEach(s -> _patch(ini.get(s), patch));
	}

	public static void patchIniWithEnvironment(Ini ini) {
		patchIni(ini, buildEnvironmentMap());
	}

	@Override
	public String factory() {
		return factory;
	}

	@Override
	public Map<String, Map<String, String>> config() {
		return config;
	}

	@Override
	public Optional<Path> configPath() {
		return Optional.of(configPath);
	}
}
