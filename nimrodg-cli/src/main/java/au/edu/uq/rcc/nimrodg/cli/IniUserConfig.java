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

import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

public class IniUserConfig implements UserConfig {

	public final String factory;
	public final Map<String, Map<String, String>> config;

	private final Map<String, Map<String, String>> actualConfig;

	public IniUserConfig(Ini ini) {
		Section _cfg = IniUserConfig.requireSection(ini, "config");
		this.factory = requireValue(_cfg, "factory");

		this.actualConfig = new HashMap<>(ini);
		
		this.config = Collections.unmodifiableMap(actualConfig);
		
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

	@Override
	public String factory() {
		return factory;
	}

	@Override
	public Map<String, Map<String, String>> config() {
		return config;
	}

}
