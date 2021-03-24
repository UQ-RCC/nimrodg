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

import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;

import java.util.List;
import java.util.Objects;

public final class MigrationPlan {

	public final SchemaVersion from;
	public final SchemaVersion to;
	public final boolean valid;
	public final String message;
	public final List<UpgradeStep> path;
	public final String sql;

	private MigrationPlan(SchemaVersion from, SchemaVersion to, boolean valid, String message, List<UpgradeStep> path, String sql) {
		this.from = Objects.requireNonNull(from, "from");
		this.to = Objects.requireNonNull(to, "to");
		this.valid = valid;
		this.message = Objects.requireNonNull(message, "message");
		this.path = Objects.requireNonNull(path, "path");
		this.sql = Objects.requireNonNull(sql, "sql");
	}

	public static MigrationPlan invalid(SchemaVersion from, SchemaVersion to, String message) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(message, "message");

		return new MigrationPlan(from, to, false, message, List.of(), "");
	}

	public static MigrationPlan valid(SchemaVersion from, SchemaVersion to, List<UpgradeStep> path) {
		StringBuilder sb = new StringBuilder();
		for(UpgradeStep us : path) {
			sb.append(us.migrationScript);
			sb.append('\n');
		}
		return new MigrationPlan(from, to, true, "", path, sb.toString());
	}
}
