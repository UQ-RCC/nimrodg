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
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodAPIDatabaseFactory;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

public class SQLite3APIFactory implements NimrodAPIDatabaseFactory {

	/* Follow Semver 2.0 for these. */
	public static final int SCHEMA_MAJOR = 4;
	public static final int SCHEMA_MINOR = 0;
	public static final int SCHEMA_PATCH = 0;

	@Override
	public NimrodAPI createNimrod(Connection conn) throws SQLException {
		checkSchemaVersion(conn);
		return new SQLite3NimrodAPI(conn);
	}

	@Override
	public NimrodAPI createNimrod(UserConfig config) {
		try {
			return createNimrod(createConnection(config, true));
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}

	@Override
	public NimrodSetupAPI getSetupAPI(Connection conn) {
		return new SQLite3SetupAPI(conn);
	}

	private static Connection createConnection(UserConfig config, boolean foreignKeys) throws SQLException {
		Map<String, String> pgconfig = config.config().get("sqlite3");
		if(pgconfig == null) {
			throw new IllegalArgumentException("No sqlite3 configuration");
		}

		Driver drv;

		try {
			drv = (Driver)Class.forName(pgconfig.getOrDefault("driver", "org.sqlite.JDBC")).getConstructor().newInstance();
		} catch(ReflectiveOperationException e) {
			throw new SQLException(e);
		}

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

	static boolean isSchemaCompatible(Connection c) throws SQLException {
		int major, minor, patch;

		try(PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'nimrod_schema_version'")) {
			try(ResultSet rs = ps.executeQuery()) {
				if(!rs.next()) {
					return false;
				}
			}
		}

		try(PreparedStatement ps = c.prepareStatement("SELECT major, minor, patch FROM nimrod_schema_version")) {
			try(ResultSet rs = ps.executeQuery()) {
				major = rs.getInt("major");
				minor = rs.getInt("minor");
				patch = rs.getInt("patch");
			}
		}

		return major == SCHEMA_MAJOR && minor <= SCHEMA_MINOR && patch <= SCHEMA_PATCH;
	}

	private static void checkSchemaVersion(Connection c) throws SQLException {
		if(!isSchemaCompatible(c)) {
			throw new SchemaMismatch(new SQLException("Incompatible schema"));
		}
	}

	@Override
	public NimrodSetupAPI getSetupAPI(UserConfig config) {
		try {
			return getSetupAPI(createConnection(config, false));
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}

}
