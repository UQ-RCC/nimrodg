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
package au.edu.uq.rcc.nimrodg.impl.sqlite3;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

public class SQLite3APIFactory implements NimrodAPIFactory {

	@Override
	public NimrodAPI createNimrod(Connection conn) throws SQLException {
		return new SQLite3NimrodAPI(conn);
	}

	@Override
	public NimrodAPI createNimrod(UserConfig config) throws SQLException, ReflectiveOperationException {
		return createNimrod(createConnection(config, true));
	}

	@Override
	public NimrodSetupAPI getSetupAPI(Connection conn) throws NimrodSetupAPI.SetupException {
		return new SQLite3SetupAPI(conn);
	}

	private static Connection createConnection(UserConfig config, boolean foreignKeys) throws SQLException, ReflectiveOperationException {
		Map<String, String> pgconfig = config.config().get("sqlite3");
		if(pgconfig == null) {
			throw new IllegalArgumentException("No sqlite3 configuration");
		}

		Driver drv = (Driver)Class.forName(pgconfig.getOrDefault("driver", "org.sqlite.JDBC")).getConstructor().newInstance();

		Connection c = drv.connect(pgconfig.getOrDefault("url", "jdbc:sqlite::memory:"), new Properties());
		if(c == null) {
			throw new SQLException("Driver returned null, check your url");
		}

		try(Statement s = c.createStatement()) {
			if(foreignKeys) {
				s.execute("PRAGMA foreign_keys = true");
			} else {
				s.execute("PRAGMA foreign_keys = false");
			}
			s.execute("PRAGMA recursive_triggers = true");
		}
		return c;
	}

	@Override
	public NimrodSetupAPI getSetupAPI(UserConfig config) throws NimrodSetupAPI.SetupException {
		try {
			return getSetupAPI(createConnection(config, false));
		} catch(SQLException | ReflectiveOperationException e) {
			throw new NimrodSetupAPI.SetupException(e);
		}
	}

}
