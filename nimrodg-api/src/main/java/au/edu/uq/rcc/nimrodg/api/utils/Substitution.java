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
package au.edu.uq.rcc.nimrodg.api.utils;

import java.math.BigDecimal;
import java.util.Objects;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public final class Substitution {

	private final String m_Name;
	private final int m_StartIndex;
	private final int m_EndIndex;
	private final int m_RelativeStartIndex;

	public Substitution(String name, int startIndex, int endIndex, int relStart) {
		if(name == null) {
			throw new IllegalArgumentException("name == null");
		} else if(startIndex < 0) {
			throw new IllegalArgumentException("startIndex < 0");
		} else if(endIndex <= startIndex) {
			throw new IllegalArgumentException("endIndex <= startIndex");
		} else if(relStart < 0) {
			throw new IllegalArgumentException("relStart < 0");
		}

		m_Name = name;
		m_StartIndex = startIndex;
		m_EndIndex = endIndex;
		m_RelativeStartIndex = relStart;

	}

	public String variable() {
		return m_Name;
	}

	public int startIndex() {
		return m_StartIndex;
	}

	public int endIndex() {
		return m_EndIndex;
	}

	public int length() {
		return m_EndIndex - m_StartIndex;
	}

	// Start index relative to the end index of the previous substitution.
	// If no previous, this is the same as startIndex().
	public int relativeStartIndex() {
		return m_RelativeStartIndex;
	}

	public JsonObject toJson() {
		return Json.createObjectBuilder()
				.add("name", m_Name)
				.add("start", m_StartIndex)
				.add("end", m_EndIndex)
				.add("relative", m_RelativeStartIndex)
				.build();
	}

	@Override
	public String toString() {
		return String.format("Sub{name=%s, start=%d, end=%d, relstart=%d}", m_Name, m_StartIndex, m_EndIndex, m_RelativeStartIndex);
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 67 * hash + Objects.hashCode(this.m_Name);
		hash = 67 * hash + this.m_StartIndex;
		hash = 67 * hash + this.m_EndIndex;
		hash = 67 * hash + this.m_RelativeStartIndex;
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
		final Substitution other = (Substitution)obj;
		if(this.m_StartIndex != other.m_StartIndex) {
			return false;
		}
		if(this.m_EndIndex != other.m_EndIndex) {
			return false;
		}
		if(this.m_RelativeStartIndex != other.m_RelativeStartIndex) {
			return false;
		}
		if(!Objects.equals(this.m_Name, other.m_Name)) {
			return false;
		}
		return true;
	}

}
