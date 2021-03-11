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

import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SetupAPIImpl implements NimrodSetupAPI {

	public static final SchemaVersion NATIVE_SCHEMA = SchemaVersion.of(5, 0, 0);

	private static final String[] DATABASE_FILES = new String[]{
		"db/00-ddl.sql",
		"db/01-ddl-experiments.sql",
		"db/02-ddl-exporttasks.sql",
		"db/03-ddl-add-compiledexperiment.sql",
		"db/04-ddl-utility.sql",
		"db/05-ddl-resources.sql",
		"db/07-ddl-messages.sql"
	};

	private final Connection conn;
	private final boolean managed;
	private final PreparedStatement qIsSchemaCompatible;
	private final PreparedStatement qGetSchemaVersion;

	public SetupAPIImpl(Connection conn) throws SQLException {
		this(conn, true);
	}

	SetupAPIImpl(Connection conn, boolean manage) throws SQLException {
		this.conn = conn;
		this.managed = manage;

		this.qIsSchemaCompatible = conn.prepareStatement("SELECT is_schema_compatible(?, ?, ?)");
		this.qGetSchemaVersion = conn.prepareStatement("SELECT major, minor, patch FROM get_schema_version()");
	}

	@Override
	public synchronized boolean isCompatibleSchema() throws SetupException {
		try {
			qIsSchemaCompatible.setInt(1, NimrodAPIFactoryImpl.NATIVE_SCHEMA.major);
			qIsSchemaCompatible.setInt(2, NimrodAPIFactoryImpl.NATIVE_SCHEMA.minor);
			qIsSchemaCompatible.setInt(3, NimrodAPIFactoryImpl.NATIVE_SCHEMA.patch);

			try(ResultSet rs = qIsSchemaCompatible.executeQuery()) {
				if(!rs.next()) {
					return false;
				}
				return rs.getBoolean(1);
			}
		} catch(SQLException e) {
			if("42883".equals(e.getSQLState())) {
				/* undefined_function */
				return false;
			}

			throw new SetupException(e);
		}
	}

	@Override
	public synchronized SchemaVersion getCurrentSchemaVersion() {
		try {
			try(ResultSet rs = qGetSchemaVersion.executeQuery()) {
				return SchemaVersion.of(rs.getInt("major"), rs.getInt("minor"), rs.getInt("patch"));
			}
		} catch(SQLException e) {
			if("42883".equals(e.getSQLState())) {
				/* undefined_function */
				return SchemaVersion.of(0, 0, 0);
			}

			throw new SetupException(e);
		}
	}

	@Override
	public SchemaVersion getNativeSchemaVersion() {
		return NATIVE_SCHEMA;
	}

	@Override
	public synchronized void reset() throws SetupException {
		String dbData;
		/* Collate all the schema files together. */
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(String s : DATABASE_FILES) {
				try(InputStream is = NimrodAPIFactoryImpl.class.getResourceAsStream(s)) {
					byte[] d = is.readAllBytes();
					baos.write(d, 0, d.length);
					/* In case the last line is a comment */
					baos.write('\n');
				}
			}

			dbData = baos.toString(StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new SetupException(e);
		}

		/* Dump everything to the database. */
		try(Statement s = conn.createStatement()) {
			s.execute(dbData);
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized void close() throws SetupException {
		try {
			if(managed) {
				conn.close();
			}
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}
}
