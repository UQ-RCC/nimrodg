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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddResource {
	public final String name;
	public final long hour;
	public final long mem;
	public final long ncpu;
	public final String machine;
	public final String account;
	public final int limit;
	public final int maxbatch;

	@JsonCreator
	public AddResource(
			@JsonProperty(value = "name", required = true) String name,
			@JsonProperty(value = "hour", required = true) int hour,
			@JsonProperty(value = "mem", required = true) int mem,
			@JsonProperty(value = "ncpu", required = true) int ncpu,
			@JsonProperty(value = "machine", required = true) String machine,
			@JsonProperty(value = "account", required = true) String account,
			@JsonProperty(value = "limit", required = true) int limit,
			@JsonProperty(value = "maxbatch", required = true) int maxbatch) {
		this.name = name;
		this.hour = hour;
		this.mem = mem;
		this.ncpu = ncpu;
		this.machine = machine;
		this.account = account;
		this.limit = limit;
		this.maxbatch = maxbatch;
	}
}
