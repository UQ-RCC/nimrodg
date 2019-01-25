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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DelayedPreparedStatement implements AutoCloseable {

	public final Connection conn;
	public final String sqlString;

	private PreparedStatement preparedStatement;

	public DelayedPreparedStatement(Connection conn, String sql) throws SQLException {
		this.conn = conn;
		this.sqlString = sql;
		this.preparedStatement = null;
	}

	public PreparedStatement get() throws SQLException {
		if(preparedStatement == null) {
			preparedStatement = conn.prepareStatement(sqlString);
		}
		return preparedStatement;
	}

	@Override
	public void close() throws SQLException {
		if(preparedStatement != null) {
			preparedStatement.close();
		}
	}

}
