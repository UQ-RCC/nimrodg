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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TempAgentInfo {

	public final long id;
	public final String platform;
	public final String path;
	public final List<Map.Entry<String, String>> posixMappings;

	private final List<Map.Entry<String, String>> actualMappings;

	public TempAgentInfo(long id, String platform, String path, List<Map.Entry<String, String>> posixMappings) {
		this.id = id;
		this.platform = platform;
		this.path = path;
		this.actualMappings = new ArrayList<>(posixMappings);
		this.posixMappings = Collections.unmodifiableList(actualMappings);
	}

	public Impl create() {
		return new Impl();
	}

	public class Impl implements AgentInfo {

		public final TempAgentInfo base;

		private Impl() {
			this.base = TempAgentInfo.this;
		}

		@Override
		public String getPlatformString() {
			return platform;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public List<Map.Entry<String, String>> posixMappings() {
			return posixMappings;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null) {
				return false;
			}
			if(getClass() != obj.getClass()) {
				return false;
			}
			final Impl other = (Impl)obj;
			return id == other.base.id;
		}
	}
}
