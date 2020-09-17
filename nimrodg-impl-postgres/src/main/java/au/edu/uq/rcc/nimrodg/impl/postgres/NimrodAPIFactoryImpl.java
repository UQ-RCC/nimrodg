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
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodAPIDatabaseFactory;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

public class NimrodAPIFactoryImpl implements NimrodAPIDatabaseFactory {

	/* Follow Semver 2.0 for these. */
	public static final int SCHEMA_MAJOR = 2;
	public static final int SCHEMA_MINOR = 0;
	public static final int SCHEMA_PATCH = 0;

	@Override
	public NimrodAPI createNimrod(Connection conn) throws SQLException {
		checkSchemaVersion(conn);
		return new NimrodAPIImpl(conn);
	}

	@Override
	public NimrodAPI createNimrod(UserConfig config) {
		try {
			return createNimrod(createConnection(config));
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}

	@Override
	public NimrodSetupAPI getSetupAPI(Connection conn) throws SQLException {
		return new SetupAPIImpl(conn);
	}

	private static Connection createConnection(UserConfig config) throws SQLException {
		Properties dbconfig = new Properties();

		Map<String, String> pgconfig = config.config().get("postgres");
		if(pgconfig == null) {
			throw new IllegalArgumentException("No postgres configuration");
		}

		dbconfig.setProperty("user", pgconfig.getOrDefault("username", ""));
		dbconfig.setProperty("password", pgconfig.getOrDefault("password", ""));
		Driver drv;

		try {
			drv = (Driver)Class.forName(pgconfig.getOrDefault("driver", "org.postgresql.Driver")).getConstructor().newInstance();
		} catch(ReflectiveOperationException e) {
			throw new SQLException(e);
		}

		Connection c = drv.connect(pgconfig.getOrDefault("url", ""), dbconfig);
		if(c == null) {
			throw new SQLException("Driver returned null, check your url");
		}

		return c;
	}

	private static void checkSchemaVersion(Connection c) throws SQLException {
		/* Check the schema version, this will RAISE if it's incompatible. */
		try(PreparedStatement ps = c.prepareStatement("SELECT require_schema_compatible(?, ?, ?)")) {
			ps.setInt(1, SCHEMA_MAJOR);
			ps.setInt(2, SCHEMA_MINOR);
			ps.setInt(3, SCHEMA_PATCH);
			ps.execute();
		} catch(SQLException e) {
			switch(e.getSQLState()) {
				case "P0001": /* raise_exception    */
				case "42883": /* undefined_function */
					throw new SchemaMismatch(e);
				default:
					throw e;
			}
		}
	}

	@Override
	public NimrodSetupAPI getSetupAPI(UserConfig config) {
		try {
			return getSetupAPI(createConnection(config));
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}


}
