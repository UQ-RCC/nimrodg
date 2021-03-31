/*
 * Nimrod Portal Backend
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
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
package au.edu.uq.rcc.nimrodg.portal;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class UserState {
	public final long id;
	public final String username;
	public final String dbUser;
	public final String dbPass;

	public final String amqpUser;
	public final String amqpPass;
	public final boolean initialised;

	public final String jdbcUrl;

	public final Map<String, String> vars;

	public UserState(long id, String username, String dbUser, String dbPass, String amqpUser, String amqpPass, boolean initialised, String jdbcUrl, Map<String, String> vars) {
		this.id = id;
		this.username = username;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
		this.amqpUser = amqpUser;
		this.amqpPass = amqpPass;
		this.initialised = initialised;
		this.jdbcUrl = jdbcUrl;
		this.vars = Map.copyOf(vars);
	}
}
