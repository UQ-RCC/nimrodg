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

import au.edu.uq.rcc.nimrodg.impl.base.db.DBBaseHelper;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgentInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DBAgentHelpers extends DBBaseHelper {

	private final PreparedStatement qLookupAgents;
	private final PreparedStatement qLookupAgentByPlatform;
	private final PreparedStatement qLookupAgentByPOSIX;

	public DBAgentHelpers(Connection conn, List<PreparedStatement> statements) throws SQLException {
		super(conn, statements);
		this.qLookupAgents = prepareStatement("SELECT * FROM nimrod_agent_info");
		this.qLookupAgentByPlatform = prepareStatement("SELECT * FROM nimrod_agent_info WHERE platform_string = ?");
		this.qLookupAgentByPOSIX = prepareStatement("SELECT * FROM nimrod_mapped_agents WHERE system = ? AND machine = ?");
	}

	public Map<String, TempAgentInfo.Impl> lookupAgents() throws SQLException {
		Map<String, TempAgentInfo.Impl> a = new HashMap<>();
		try(ResultSet rs = qLookupAgents.executeQuery()) {
			while(rs.next()) {
				TempAgentInfo tai = tempAgentFromRow(rs);
				a.put(tai.platform, tai.create());
			}
		}
		return a;
	}

	public Optional<TempAgentInfo> lookupAgentByPlatform(String plat) throws SQLException {
		qLookupAgentByPlatform.setString(1, plat);
		try(ResultSet rs = qLookupAgentByPlatform.executeQuery()) {
			if(rs.next()) {
				return Optional.of(tempAgentFromRow(rs));
			} else {
				return Optional.empty();
			}
		} finally {
			qLookupAgentByPlatform.clearParameters();
		}
	}

	public Optional<TempAgentInfo> lookupAgentByPOSIX(String system, String machine) throws SQLException {
		qLookupAgentByPOSIX.setString(1, system);
		qLookupAgentByPOSIX.setString(2, machine);
		try(ResultSet rs = qLookupAgentByPOSIX.executeQuery()) {
			if(rs.next()) {
				return Optional.of(tempAgentFromRow(rs));
			} else {
				return Optional.empty();
			}
		} finally {
			qLookupAgentByPOSIX.clearParameters();
		}
	}

	private static TempAgentInfo tempAgentFromRow(ResultSet rs) throws SQLException {
		return new TempAgentInfo(
				rs.getLong("id"),
				rs.getString("platform_string"),
				rs.getString("path"),
				Stream.of((String[][])(rs.getArray("mappings").getArray()))
						.map(s -> new AbstractMap.SimpleImmutableEntry<>(s[0], s[1]))
						.collect(Collectors.toList())
		);
	}

}
