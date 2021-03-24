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
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.MigrationPlan;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodAPIDatabaseFactory;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.impl.base.db.UpgradeStep;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SQLite3APIFactory implements NimrodAPIDatabaseFactory {

	public static final SchemaVersion NATIVE_SCHEMA = SchemaVersion.of(4, 0, 0);

	public static final MigrationPlan RESET_PLAN;

	public static final List<UpgradeStep> UPGRADE_STEPS;
	private static final Map<SchemaVersion, UpgradeStep> UPGRADE_STEP_MAP;

	static {
		RESET_PLAN = MigrationPlan.valid(SchemaVersion.UNVERSIONED, NATIVE_SCHEMA, List.of(UpgradeStep.of(
				SchemaVersion.UNVERSIONED, NATIVE_SCHEMA, DBUtils.combineEmbeddedFiles(
						SQLite3APIFactory.class,
						"db/setup/00-config.sql",
						"db/setup/01-experiments.sql",
						"db/setup/02-resources.sql",
						"db/setup/03-messages.sql"
				)
		)));

		UPGRADE_STEPS = List.of(
				UpgradeStep.of(
						SchemaVersion.of(1, 0, 0),
						SchemaVersion.of(2, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(SQLite3APIFactory.class, "db/setup/upgrade/1.0.0_to_2.0.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(2, 0, 0),
						SchemaVersion.of(2, 1, 0),
						NimrodUtils.readEmbeddedFileAsString(SQLite3APIFactory.class, "db/setup/upgrade/2.0.0_to_2.1.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(2, 1, 0),
						SchemaVersion.of(3, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(SQLite3APIFactory.class, "db/setup/upgrade/2.1.0_to_3.0.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(3, 0, 0),
						SchemaVersion.of(4, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(SQLite3APIFactory.class, "db/setup/upgrade/3.0.0_to_4.0.0.sql")
				)
		);

		UPGRADE_STEP_MAP = new HashMap<>();
		for(UpgradeStep s : UPGRADE_STEPS) {
			UPGRADE_STEP_MAP.put(s.from, s);
		}
	}

	@Override
	public NimrodAPI createNimrod(Connection conn) throws SQLException {
		checkSchemaVersion(conn);
		return new SQLite3NimrodAPI(conn);
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
	public NimrodSetupAPI getSetupAPI(Connection conn) {
		return new SQLite3SetupAPI(conn);
	}

	@Override
	public Connection createConnection(UserConfig config) throws SQLException {
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
			s.execute("PRAGMA foreign_keys = true");
			s.execute("PRAGMA recursive_triggers = true");
		}
		return c;
	}

	@Override
	public SchemaVersion getNativeSchemaVersion() {
		return NATIVE_SCHEMA;
	}

	@Override
	public SchemaVersion getCurrentSchemaVersion(Connection conn) throws SQLException {
		try(PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'nimrod_schema_version'")) {
			try(ResultSet rs = ps.executeQuery()) {
				if(!rs.next()) {
					return SchemaVersion.UNVERSIONED;
				}
			}
		}

		try(PreparedStatement ps = conn.prepareStatement("SELECT major, minor, patch FROM nimrod_schema_version")) {
			try(ResultSet rs = ps.executeQuery()) {
				return SchemaVersion.of(
						rs.getInt("major"),
						rs.getInt("minor"),
						rs.getInt("patch")
				);
			}
		}
	}

	@Override
	public MigrationPlan buildResetPlan() {
		return RESET_PLAN;
	}

	private void checkSchemaVersion(Connection c) throws SQLException {
		if(!getCurrentSchemaVersion(c).isCompatible(NATIVE_SCHEMA)) {
			throw new SchemaMismatch(new SQLException("Incompatible schema"));
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
