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

import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;

public class TempConfig {

	public final String workDir;
	public final String storeDir;
	public final NimrodURI amqpUri;
	public final String amqpRoutingKey;
	public final NimrodURI txUri;

	public TempConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri) {
		this.workDir = workDir;
		this.storeDir = storeDir;
		this.amqpUri = amqpUri;
		this.amqpRoutingKey = amqpRoutingKey;
		this.txUri = txUri;
	}

	public Impl create() {
		return new Impl();
	}

	public class Impl implements NimrodConfig {

		public final TempConfig base;

		private Impl() {
			this.base = TempConfig.this;
		}

		@Override
		public String getWorkDir() {
			return workDir;
		}

		@Override
		public String getRootStore() {
			return storeDir;
		}

		@Override
		public NimrodURI getAmqpUri() {
			return amqpUri;
		}

		@Override
		public String getAmqpRoutingKey() {
			return amqpRoutingKey;
		}

		@Override
		public NimrodURI getTransferUri() {
			return txUri;
		}

	}
}
