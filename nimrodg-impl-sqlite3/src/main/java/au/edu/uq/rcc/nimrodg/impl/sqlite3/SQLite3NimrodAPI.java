/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempNimrodAPIImpl;
import java.sql.Connection;
import java.sql.SQLException;

public class SQLite3NimrodAPI extends TempNimrodAPIImpl {
	
	private final Connection conn;
	
	public SQLite3NimrodAPI(Connection conn) throws SQLException {
		super(new SQLite3DB(conn));
		this.conn = conn;
	}
	
	@Override
	public void close() {
		try(conn) {
			super.close();
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}

	@Override
	public Connection getConnection() {
		return conn;
	}

}
