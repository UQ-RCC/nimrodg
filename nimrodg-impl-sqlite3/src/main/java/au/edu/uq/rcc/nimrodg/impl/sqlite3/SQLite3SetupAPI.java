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

import au.edu.uq.rcc.nimrodg.impl.base.db.SQLUUUUU;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI.SetupException;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite3 Setup API for Nimrod.
 */
public class SQLite3SetupAPI extends SQLUUUUU<SetupException> implements NimrodSetupAPI {

	private static final String[] DATABASE_FILES = new String[]{
		"db/setup/00-config.sql",
		"db/setup/01-experiments.sql",
		"db/setup/02-resources.sql",
		"db/setup/03-messages.sql"
	};

	private final Connection conn;
	private final boolean managed;

	SQLite3SetupAPI(Connection conn) {
		this(conn, true);
	}

	SQLite3SetupAPI(Connection conn, boolean managed) {
		this.conn = conn;
		this.managed = managed;
	}

	@Override
	public synchronized void reset() throws SetupException {
		String dbData;
		/* Collate all the schema files together. */
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(String s : DATABASE_FILES) {
				baos.write(NimrodUtils.readEmbeddedFile(SQLite3APIFactory.class, s));
				/* In case the last line is a comment */
				baos.write('\n');
			}

			dbData = baos.toString(StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new SetupException(e);
		}

		/* Dump everything to the database. */
		this.runSQLTransaction(() -> {
			try(Statement s = conn.createStatement()) {
				s.executeUpdate(dbData);
			}
		});
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

	@Override
	protected Connection getConnection() {
		return conn;
	}

	@Override
	protected SetupException makeException(SQLException e) {
		return new SetupException(e);
	}
}
