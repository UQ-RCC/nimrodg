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
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI.SetupException;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

public class NimrodAPIFactoryImpl implements NimrodAPIFactory {

	@Override
	public NimrodAPI createNimrod(Connection conn) throws SQLException {
		return new NimrodAPIImpl(conn);
	}

	@Override
	public NimrodAPI createNimrod(UserConfig config) throws SQLException, ReflectiveOperationException {
		return createNimrod(createConnection(config));
	}

	@Override
	public NimrodSetupAPI getSetupAPI(Connection conn) throws SQLException {
		return new SetupAPIImpl(conn);
	}

	private static Connection createConnection(UserConfig config) throws SQLException, ReflectiveOperationException {
		Properties dbconfig = new Properties();

		Map<String, String> pgconfig = config.config().get("postgres");
		if(pgconfig == null) {
			throw new IllegalArgumentException("No postgres configuration");
		}

		dbconfig.setProperty("user", pgconfig.getOrDefault("username", ""));
		dbconfig.setProperty("password", pgconfig.getOrDefault("password", ""));
		Driver drv = (Driver)Class.forName(pgconfig.getOrDefault("driver", "org.postgresql.Driver")).getConstructor().newInstance();

		Connection c = drv.connect(pgconfig.getOrDefault("url", ""), dbconfig);
		if(c == null) {
			throw new SQLException("Driver returned null, check your url");
		}

		return c;
	}

	@Override
	public NimrodSetupAPI getSetupAPI(UserConfig config) throws SetupException {
		try {
			return getSetupAPI(createConnection(config));
		} catch(SQLException|ReflectiveOperationException e) {
			throw new SetupException(e);
		}
	}
}
