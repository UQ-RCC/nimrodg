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

import java.util.Objects;

public final class UpgradeStep {
	public final SchemaVersion from;
	public final SchemaVersion to;
	public final String migrationScript;

	private UpgradeStep(SchemaVersion from, SchemaVersion to, String migrationScript) {
		this.from = Objects.requireNonNull(from, "from");
		this.to = Objects.requireNonNull(to, "to");
		this.migrationScript = Objects.requireNonNull(migrationScript, "migrationScript");
	}

	public static UpgradeStep of(SchemaVersion from, SchemaVersion to, String migrationScript) {
		return new UpgradeStep(from, to, migrationScript);
	}

}