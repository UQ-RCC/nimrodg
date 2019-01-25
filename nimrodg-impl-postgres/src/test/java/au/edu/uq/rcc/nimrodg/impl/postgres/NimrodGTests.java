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
package au.edu.uq.rcc.nimrodg.impl.postgres;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.test.APITests;
import au.edu.uq.rcc.nimrodg.test.TestConfig;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class NimrodGTests extends APITests {

	public NimrodAPI nimrod;

	@Rule
	public TemporaryFolder tmpDir = new TemporaryFolder();

	@Before
	public void setupDb() throws Exception {
		Path root = tmpDir.getRoot().toPath();

		String pguser = System.getenv("NIMRODG_TEST_PGUSER");
		String pgpassword = System.getenv("NIMRODG_TEST_PGPASSWORD");
		String pghost = System.getenv("NIMRODG_TEST_PGHOST");
		String pgdatabase = System.getenv("NIMRODG_TEST_PGDATABASE");

		if(pguser == null || pgpassword == null || pghost == null || pgdatabase == null) {
			throw new IllegalArgumentException("NIMRODG_TEST_* environment variables not set.");
		}

		UserConfig ucfg = new UserConfig() {
			@Override
			public String factory() {
				return NimrodAPIFactoryImpl.class.getCanonicalName();
			}

			@Override
			public Map<String, Map<String, String>> config() {
				return new HashMap<String, Map<String, String>>() {
					{
						put("config", new HashMap<String, String>() {
							{
								put("factory", NimrodAPIFactoryImpl.class.getCanonicalName());
							}
						});

						put("postgres", new HashMap<String, String>() {
							{
								put("driver", "org.postgresql.Driver");
								put("url", String.format("jdbc:postgresql://%s/%s", pghost, pgdatabase));
								put("username", pguser);
								put("password", pgpassword);
							}
						});
					}
				};
			}
		};

		NimrodAPIFactoryImpl fimpl = new NimrodAPIFactoryImpl();
		try(NimrodSetupAPI api = fimpl.getSetupAPI(ucfg)) {
			api.reset();
			api.setup(new TestConfig(root));
		}

		nimrod = fimpl.createNimrod(ucfg);
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
}
