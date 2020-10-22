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
package au.edu.uq.rcc.nimrodg.api;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class NimrodException extends RuntimeException {

	public NimrodException() {

	}

	public NimrodException(Throwable t) {
		super(t);
	}

	public NimrodException(String fmt, Object... args) {
		super(String.format(fmt, args));
	}

	public static class ExperimentExists extends NimrodException {
		public final Experiment experiment;

		public ExperimentExists(Experiment experiment) {
			this.experiment = experiment;
		}
	}

	public static class ExperimentActive extends NimrodException {
		public final Experiment experiment;

		public ExperimentActive(Experiment exp) {
			this.experiment = exp;

		}
	}

	public static class DifferentImplementation extends NimrodException {

	}

	public static class ResourceExists extends NimrodException {
		public final Resource resource;

		public ResourceExists(Resource resource) {
			this.resource = resource;
		}
	}

	public static class ResourceBusy extends NimrodException {
		public final Resource resource;

		public ResourceBusy(Resource res) {
			this.resource = res;
		}
	}

	public static class ResourceFull extends NimrodException {
		public final Resource resource;

		public ResourceFull(Resource res) {
			this.resource = res;
		}
	}

	public static class InvalidResourceType extends NimrodException {
		public final String name;

		public InvalidResourceType(String name) {
			this.name = Objects.requireNonNull(name, "name");
		}
	}

	public static class InvalidResourceConfiguration extends NimrodException {
		public final ResourceType type;
		public final List<String> messages;

		public InvalidResourceConfiguration(ResourceType type, List<String> messages) {
			this.type = Objects.requireNonNull(type, "type");
			this.messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
		}
	}

	public static class DbError extends NimrodException {
		public final SQLException sql;

		public DbError(SQLException sql) {
			super(sql);
			this.sql = sql;
		}
	}
}
