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
package au.edu.uq.rcc.nimrodg.impl.sqlite3;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.test.APITests;
import au.edu.uq.rcc.nimrodg.test.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

public class SQLite3Tests extends APITests {

	public NimrodAPI nimrod;

	@TempDir
	public Path root;

	@BeforeEach
	public void setupDb() throws Exception {
		nimrod = createTestNimrod(root);
	}

	public static NimrodAPI createTestNimrod(Path root) throws Exception {
		Path dbPath = root.resolve("nimrod.db");
		UserConfig ucfg = new UserConfig() {
			@Override
			public String factory() {
				return SQLite3APIFactory.class.getCanonicalName();
			}

			@Override
			public Map<String, Map<String, String>> config() {
				return Map.of(
						"config", Map.of("factory", SQLite3APIFactory.class.getCanonicalName()),
						"sqlite3", Map.of(
								"driver", "org.sqlite.JDBC",
								"url", String.format("jdbc:sqlite://%s", dbPath.toString())
						)
				);
			}
		};

		return TestUtils.resetAndCreateNimrod(new SQLite3APIFactory(), ucfg, APITests.getTestSetupConfig(root));
	}

	@AfterEach
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
