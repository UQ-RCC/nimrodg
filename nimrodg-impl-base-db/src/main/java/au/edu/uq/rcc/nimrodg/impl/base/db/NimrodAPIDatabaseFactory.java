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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public interface NimrodAPIDatabaseFactory extends NimrodAPIFactory {

	class SchemaMismatch extends NimrodException.DbError {
		public SchemaMismatch(SQLException sql) {
			super(sql);
		}
	}

	NimrodAPI createNimrod(Connection conn) throws SQLException;

	Connection createConnection(UserConfig config) throws SQLException;

	SchemaVersion getNativeSchemaVersion();

	SchemaVersion getCurrentSchemaVersion(Connection conn) throws SQLException;

	MigrationPlan buildResetPlan();

	default MigrationPlan buildUpgradePlan(Connection conn) throws SQLException {
		Objects.requireNonNull(conn, "conn");

		SchemaVersion curr = getCurrentSchemaVersion(conn);
		if(SchemaVersion.UNVERSIONED.equals(curr)) {
			return buildResetPlan();
		}

		SchemaVersion nat = getNativeSchemaVersion();

		if(curr.isCompatible(nat)) {
			return MigrationPlan.valid(curr, curr, List.of());
		}

		return buildMigrationPlan(curr, getNativeSchemaVersion());
	}

	MigrationPlan buildMigrationPlan(SchemaVersion from, SchemaVersion to);

	List<UpgradeStep> getUpgradePairs();
}
