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
package au.edu.uq.rcc.nimrodg.impl.postgres;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * THIS IS LEGACY
 */
public class DBHelpers {

	public static String getProperty(String key, PreparedStatement ps) throws SQLException {
		ps.setString(1, key);

		try(ResultSet rs = ps.executeQuery()) {
			if(rs.next()) {
				return rs.getString("value");
			} else {
				return null;
			}
		} finally {
			ps.clearParameters();
		}
	}

	public static String setProperty(String key, String value, PreparedStatement ps) throws SQLException {
		ps.setString(1, key);
		ps.setString(2, value);

		try(ResultSet rs = ps.executeQuery()) {
			if(rs.next()) {
				return rs.getString("value");
			} else {
				return null;
			}
		} finally {
			ps.clearParameters();
		}
	}

	public static Map<String, String> getProperties(PreparedStatement ps) throws SQLException {
		Map<String, String> c = new HashMap<>();
		try(ResultSet rs = ps.executeQuery()) {
			while(rs.next()) {
				c.put(rs.getString("key"), rs.getString("value"));
			}
		}

		return c;
	}
}
