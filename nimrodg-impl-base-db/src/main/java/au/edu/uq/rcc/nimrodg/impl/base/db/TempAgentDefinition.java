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

import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.MachinePair;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public class TempAgentDefinition {

	public final long id;
	public final String platform;
	public final String path;
	public final Set<MachinePair> posixMappings;

	public TempAgentDefinition(long id, String platform, String path, Set<MachinePair> posixMappings) {
		this.id = id;
		this.platform = platform;
		this.path = path;
		this.posixMappings = posixMappings;
	}

	public Impl create() {
		return new Impl();
	}

	public class Impl implements AgentDefinition {

		public final TempAgentDefinition base;
		public final Path path;
		public final Set<MachinePair> pairs;

		private Impl() {
			this.base = TempAgentDefinition.this;
			this.path = Path.of(this.base.path);
			this.pairs = Collections.unmodifiableSet(this.base.posixMappings);
		}

		@Override
		public String getPlatformString() {
			return platform;
		}

		@Override
		public Path getPath() {
			return path;
		}

		@Override
		public Set<MachinePair> posixMappings() {
			return pairs;
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
