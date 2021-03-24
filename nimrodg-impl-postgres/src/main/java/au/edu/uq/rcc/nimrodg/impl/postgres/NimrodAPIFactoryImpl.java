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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class NimrodAPIFactoryImpl implements NimrodAPIDatabaseFactory {

	public static final SchemaVersion NATIVE_SCHEMA = SchemaVersion.of(5, 0, 0);

	public static final MigrationPlan RESET_PLAN;

	public static final List<UpgradeStep> UPGRADE_STEPS;
	private static final Map<SchemaVersion, UpgradeStep> UPGRADE_STEP_MAP;

	static {
		RESET_PLAN = MigrationPlan.valid(SchemaVersion.UNVERSIONED, NATIVE_SCHEMA, List.of(UpgradeStep.of(
				SchemaVersion.UNVERSIONED, NATIVE_SCHEMA, DBUtils.combineEmbeddedFiles(
						NimrodAPIFactoryImpl.class,
						"db/00-ddl.sql",
						"db/01-ddl-experiments.sql",
						"db/02-ddl-exporttasks.sql",
						"db/03-ddl-add-compiledexperiment.sql",
						"db/04-ddl-utility.sql",
						"db/05-ddl-resources.sql",
						"db/07-ddl-messages.sql"
				)
		)));

		UPGRADE_STEPS = List.of(
				UpgradeStep.of(
						SchemaVersion.of(1, 0, 0),
						SchemaVersion.of(2, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(NimrodAPIFactoryImpl.class, "db/upgrade/1.0.0_to_2.0.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(2, 0, 0),
						SchemaVersion.of(2, 1, 0),
						NimrodUtils.readEmbeddedFileAsString(NimrodAPIFactoryImpl.class, "db/upgrade/2.0.0_to_2.1.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(2, 1, 0),
						SchemaVersion.of(3, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(NimrodAPIFactoryImpl.class, "db/upgrade/2.1.0_to_3.0.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(3, 0, 0),
						SchemaVersion.of(4, 0, 0),
						NimrodUtils.readEmbeddedFileAsString(NimrodAPIFactoryImpl.class, "db/upgrade/3.0.0_to_4.0.0.sql")
				),
				UpgradeStep.of(
						SchemaVersion.of(4, 0, 0),
						NATIVE_SCHEMA,
						NimrodUtils.readEmbeddedFileAsString(NimrodAPIFactoryImpl.class, "db/upgrade/4.0.0_to_5.0.0.sql")
				)
		);

		UPGRADE_STEP_MAP = new HashMap<>(UPGRADE_STEPS.size());
		for(UpgradeStep s : UPGRADE_STEPS) {
			UPGRADE_STEP_MAP.put(s.from, s);
		}
	}

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

	@Override
	public Connection createConnection(UserConfig config) throws SQLException {
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

	@Override
	public SchemaVersion getNativeSchemaVersion() {
		return NATIVE_SCHEMA;
	}

	@Override
	public SchemaVersion getCurrentSchemaVersion(Connection conn) throws SQLException {
		try(PreparedStatement ps = conn.prepareStatement("SELECT * FROM get_schema_version()")) {
			try(ResultSet rs = ps.executeQuery()) {
				if(!rs.next()) {
					throw new SQLException("get_schema_version() returned no rows");
				}
				return SchemaVersion.of(rs.getInt(1), rs.getInt(2), rs.getInt(3));
			}
		} catch(SQLException e) {
			if("42883".equals(e.getSQLState())) {
				/* undefined_function */
				return SchemaVersion.UNVERSIONED;
			}

			throw e;
		}
	}

	@Override
	public MigrationPlan buildResetPlan() {
		return RESET_PLAN;
	}

	private static void checkSchemaVersion(Connection c) throws SQLException {
		/* Check the schema version, this will RAISE if it's incompatible. */
		try(PreparedStatement ps = c.prepareStatement("SELECT require_schema_compatible(?, ?, ?)")) {
			ps.setInt(1, NATIVE_SCHEMA.major);
			ps.setInt(2, NATIVE_SCHEMA.minor);
			ps.setInt(3, NATIVE_SCHEMA.patch);
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
	public MigrationPlan buildMigrationPlan(final SchemaVersion from, final SchemaVersion to) {
		if(to.compareTo(NATIVE_SCHEMA) > 0) {
			return MigrationPlan.invalid(from, to, "Target version too new");
		}

		return DBUtils.buildMigrationPlan(from, to, UPGRADE_STEP_MAP);
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
