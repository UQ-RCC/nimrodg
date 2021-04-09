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
package au.edu.uq.rcc.nimrodg.setup;

import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.ResourceType;

import java.nio.file.Path;

public interface NimrodSetupAPI extends AutoCloseable {

	class SetupException extends NimrodException {

		public SetupException() {
		}

		public SetupException(String msg) {
			super(msg);
		}

		public SetupException(String msg, Throwable t) {
			super(msg, t);
		}

		public SetupException(Throwable t) {
			super(t);
		}

	}

	/**
	 * Check if the database schema is compatible with the current implementation.
	 *
	 * This should be tested before any other operation (except {@link NimrodSetupAPI#reset()}.
	 *
	 * @return if the database schema is compatible with the current implementation.
	 */
	boolean isCompatibleSchema();

	void reset() throws SetupException;

	void setup(SetupConfig cfg) throws SetupException;

	String getProperty(String prop) throws SetupException;

	String setProperty(String prop, String val) throws SetupException;

	default boolean addResourceType(String name, Class<? extends ResourceType> clazz) throws SetupException {
		if(name == null || clazz == null) {
			throw new IllegalArgumentException();
		}

		return addResourceType(name, clazz.getCanonicalName());
	}

	boolean addResourceType(String name, String clazz) throws SetupException;

	boolean deleteResourceType(String name) throws SetupException;

	boolean addAgent(String platformString, Path path) throws SetupException;

	boolean deleteAgent(String platformString) throws SetupException;

	boolean mapAgent(String platformString, String system, String machine) throws SetupException;

	boolean unmapAgent(String system, String machine) throws SetupException;

	@Override
	void close() throws SetupException;

}