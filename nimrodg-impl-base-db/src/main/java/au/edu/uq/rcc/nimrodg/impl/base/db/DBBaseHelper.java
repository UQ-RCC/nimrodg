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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DBBaseHelper {

	protected final Connection conn;
	protected final List<PreparedStatement> statements;

	protected DBBaseHelper(Connection conn, List<PreparedStatement> statements) {
		this.conn = conn;
		this.statements = statements;
	}

	protected final PreparedStatement prepareStatement(String s) throws SQLException {
		return prepareStatement(s, false);
	}

	protected final PreparedStatement prepareStatement(String s, boolean returnKeys) throws SQLException {

		PreparedStatement ps;
		if(returnKeys) {
			ps = conn.prepareStatement(s, Statement.RETURN_GENERATED_KEYS);
		} else {
			ps = conn.prepareStatement(s);
		}
		statements.add(ps);
		return ps;
	}
}
