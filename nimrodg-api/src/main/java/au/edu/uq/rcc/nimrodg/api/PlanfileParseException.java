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

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class PlanfileParseException extends RuntimeException {

	public static class ParseError {

		public final int line;
		public final int position;
		public final String message;

		private ParseError(int line, int position, String message) {
			this.line = line;
			this.position = position;
			this.message = message;
		}

		public String toString(String fileName) {
			return String.format("%s: %d:%d: %s", fileName, line, position, message);
		}

		@Override
		public String toString() {
			return String.format("%d:%d: %s", line, position, message);
		}
	}

	private final TreeSet<ParseError> errors;

	public PlanfileParseException() {
		this.errors = new TreeSet<>((a, b) -> {
			int val = Integer.compare(a.line, b.line);
			if(val == 0) {
				val = Integer.compare(a.position, b.position);
			}
			return val;
		});
	}

	public void addError(int line, int position, String message) {
		this.errors.add(new ParseError(line, position, message));
	}

	public Set<ParseError> getErrors() {
		return Collections.unmodifiableSet(errors);
	}
}
