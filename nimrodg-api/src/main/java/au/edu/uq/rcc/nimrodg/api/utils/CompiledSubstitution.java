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
package au.edu.uq.rcc.nimrodg.api.utils;

import au.edu.uq.rcc.nimrodg.api.Substitution;

import java.util.Objects;

public final class CompiledSubstitution implements Substitution {

	private final String name;
	private final int startIndex;
	private final int endIndex;
	private final int relativeStartIndex;

	public CompiledSubstitution(String name, int startIndex, int endIndex, int relStart) {
		if(name == null) {
			throw new IllegalArgumentException("name == null");
		} else if(startIndex < 0) {
			throw new IllegalArgumentException("startIndex < 0");
		} else if(endIndex <= startIndex) {
			throw new IllegalArgumentException("endIndex <= startIndex");
		} else if(relStart < 0) {
			throw new IllegalArgumentException("relStart < 0");
		}

		this.name = name;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		relativeStartIndex = relStart;

	}

	@Override
	public String getVariable() {
		return name;
	}

	@Override
	public int getStartIndex() {
		return startIndex;
	}

	@Override
	public int getEndIndex() {
		return endIndex;
	}

	@Override
	public int getLength() {
		return endIndex - startIndex;
	}

	// Start index relative to the end index of the previous substitution.
	// If no previous, this is the same as startIndex().
	@Override
	public int getRelativeStartIndex() {
		return relativeStartIndex;
	}

	@Override
	public String toString() {
		return String.format("Sub{name=%s, start=%d, end=%d, relstart=%d}", name, startIndex, endIndex, relativeStartIndex);
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 67 * hash + Objects.hashCode(this.name);
		hash = 67 * hash + this.startIndex;
		hash = 67 * hash + this.endIndex;
		hash = 67 * hash + this.relativeStartIndex;
		return hash;
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
		final CompiledSubstitution other = (CompiledSubstitution)obj;
		if(this.startIndex != other.startIndex) {
			return false;
		}
		if(this.endIndex != other.endIndex) {
			return false;
		}
		if(this.relativeStartIndex != other.relativeStartIndex) {
			return false;
		}
		if(!Objects.equals(this.name, other.name)) {
			return false;
		}
		return true;
	}

}
