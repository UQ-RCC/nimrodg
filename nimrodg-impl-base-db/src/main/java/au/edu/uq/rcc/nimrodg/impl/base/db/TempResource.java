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

import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.utils.ResourceUtils;
import javax.json.JsonObject;
import au.edu.uq.rcc.nimrodg.api.Resource;

public class TempResource {

	public TempResource(long id, String name, String typeName, String typeClass, JsonObject config, NimrodURI amqpUri, NimrodURI txUri) {
		this.id = id;
		this.name = name;
		this.typeName = typeName;
		this.typeClass = typeClass;
		this.config = config;
		this.amqpUri = amqpUri;
		this.txUri = txUri;
	}

	public final long id;
	public final String name;
	public final String typeName;
	public final String typeClass;
	public final JsonObject config;
	public final NimrodURI amqpUri;
	public final NimrodURI txUri;

	public Impl create(NimrodDBAPI db) {
		return new Impl(db);
	}

	public class Impl implements Resource {

		protected final NimrodDBAPI db;

		public final TempResource base;

		protected Impl(NimrodDBAPI db) {
			this.db = db;
			this.base = TempResource.this;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getPath() {
			return name;
		}

		@Override
		public String getTypeName() {
			return typeName;
		}

		@Override
		public ResourceType getType() {
			try {
				return ResourceUtils.createType(typeClass);
			} catch(ReflectiveOperationException ex) {
				throw new NimrodAPIException(ex);
			}
		}

		@Override
		public JsonObject getConfig() {
			return config;
		}

		@Override
		public NimrodURI getTransferUri() {
			return txUri;
		}

		@Override
		public NimrodURI getAMQPUri() {
			return amqpUri;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof Impl)) {
				return false;
			}

			Impl node = (Impl)obj;

			return id == node.base.id;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}

		@Override
		public String toString() {
			return base.name;
		}
	}
}
