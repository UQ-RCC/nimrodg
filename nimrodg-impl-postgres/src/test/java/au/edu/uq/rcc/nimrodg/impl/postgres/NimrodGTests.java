/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
package au.edu.uq.rcc.nimrodg.impl.postgres;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.test.APITests;
import java.nio.file.Path;
import java.util.Map;

import au.edu.uq.rcc.nimrodg.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class NimrodGTests extends APITests {

	public NimrodAPI nimrod;
	public Path root;

	@Rule
	public TemporaryFolder tmpDir = new TemporaryFolder();

	@Before
	public void setupDb() throws Exception {
		root = tmpDir.getRoot().toPath();

		TestInfo testInfo = TestInfo.getBestEffort();

		UserConfig ucfg = new UserConfig() {
			@Override
			public String factory() {
				return NimrodAPIFactoryImpl.class.getCanonicalName();
			}

			@Override
			public Map<String, Map<String, String>> config() {
				return Map.of(
						"config", Map.of("factory", NimrodAPIFactoryImpl.class.getCanonicalName()),
						"postgres", testInfo.buildJdbcConfig()
				);
			}
		};

		nimrod = TestUtils.resetAndCreateNimrod(new NimrodAPIFactoryImpl(), ucfg, APITests.getTestSetupConfig(root));
	}

	@After
	public void closeDb() throws Exception {
		if(nimrod != null) {
			nimrod.close();
		}
	}

	@Override
	protected NimrodAPI getNimrod() {
		return nimrod;
	}

	@Override
	protected Path getRoot() {
		return root;
	}
}
