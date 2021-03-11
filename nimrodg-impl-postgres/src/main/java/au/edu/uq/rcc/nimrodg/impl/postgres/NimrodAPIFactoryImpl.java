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
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import au.edu.uq.rcc.nimrodg.impl.base.db.UpgradeStep;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class NimrodAPIFactoryImpl implements NimrodAPIDatabaseFactory {

	public static final SchemaVersion NATIVE_SCHEMA = SchemaVersion.of(5, 0, 0);

	public static final List<UpgradeStep> UPGRADE_STEPS;
	private static final Map<SchemaVersion, UpgradeStep> UPGRADE_STEP_MAP;
	static {
		try {
			UPGRADE_STEPS = List.of(
					UpgradeStep.of(
							SchemaVersion.of(0, 0, 0),
							SchemaVersion.of(1, 0, 0),
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/0.0.0_to_1.0.0.sql").toURI()
					),
					UpgradeStep.of(
							SchemaVersion.of(1, 0, 0),
							SchemaVersion.of(2, 0, 0),
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/1.0.0_to_2.0.0.sql").toURI()
					),
					UpgradeStep.of(
							SchemaVersion.of(2, 0, 0),
							SchemaVersion.of(2, 1, 0),
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/2.0.0_to_2.1.0.sql").toURI()
					),
					UpgradeStep.of(
							SchemaVersion.of(2, 1, 0),
							SchemaVersion.of(3, 0, 0),
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/2.1.0_to_3.0.0.sql").toURI()
					),
					UpgradeStep.of(
							SchemaVersion.of(3, 0, 0),
							SchemaVersion.of(4, 0, 0),
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/3.0.0_to_4.0.0.sql").toURI()
					),
					UpgradeStep.of(
							SchemaVersion.of(4, 0, 0),
							NATIVE_SCHEMA,
							NimrodAPIFactoryImpl.class.getResource("db/upgrade/4.0.0_to_5.0.0.sql").toURI()
					)
			);
		} catch(URISyntaxException e) {
			throw new RuntimeException(e);
		}

		UPGRADE_STEP_MAP = new HashMap<>();
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
	public SchemaVersion getNativeSchemaVersion() {
		return NATIVE_SCHEMA;
	}

	public SchemaVersion getCurrentSchemaVersion(Connection conn) throws SQLException {
		try(PreparedStatement ps = conn.prepareStatement("SELECT * FROM get_schema_version()")) {
			try(ResultSet rs = ps.executeQuery()) {
				return SchemaVersion.of(rs.getInt(1), rs.getInt(2), rs.getInt(3));
			}
		} catch(SQLException e) {
			if("42883".equals(e.getSQLState())) {
				/* undefined_function */
				return SchemaVersion.of(0, 0, 0);
			}

			throw e;
		}
	}

	@Override
	public void upgradeSchemaTo(Connection conn, SchemaVersion targetVersion) throws SQLException {
		if(targetVersion.compareTo(NATIVE_SCHEMA) > 0) {
			throw new IllegalArgumentException("targetVersion");
		}

		SchemaVersion currVer = getCurrentSchemaVersion(conn);
		if(currVer.compareTo(NATIVE_SCHEMA) > 0) {
			/* Can't upgrade to an older version. */
			throw new IllegalArgumentException();
		}

		if(currVer.compareTo(SchemaVersion.of(1, 0, 0)) < 0) {
			/*
			 * Due to early screw-ups, this can't safely be done automatically.
			 * Upgrade manually to 1.0.0 first, then we can do things.
			 */
			throw new IllegalArgumentException();
		}

		/* Find an upgrade path. */
		List<UpgradeStep> steps = new ArrayList<>(UPGRADE_STEPS.size());
		while(currVer.compareTo(targetVersion) < 0) {
			UpgradeStep step = UPGRADE_STEP_MAP.get(currVer);
			if(step == null) {
				throw new IllegalStateException("Unable to determine upgrade path from " + currVer);
			}

			assert step.from.equals(currVer);
			assert step.from.compareTo(step.to) < 0;

			steps.add(step);
			currVer = step.to;
		}

		/* Build the upgrade script. */
		StringBuilder sb = new StringBuilder();
		for(UpgradeStep us : steps) {
			try {
				sb.append(Files.readString(Path.of(us.migrationScript), StandardCharsets.UTF_8));
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
			sb.append('\n');
		}

		/* Do it. */
		try(Statement s = conn.createStatement()) {
			s.execute(sb.toString());
		}
	}

	@Override
	public List<UpgradeStep> getUpgradePairs() {
		return UPGRADE_STEPS;
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
