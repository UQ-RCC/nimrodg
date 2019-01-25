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
import au.edu.uq.rcc.nimrodg.api.Substitution;

public final class SubstitutionImpl implements Substitution {

	SubstitutionImpl(String var, int start, int end, int relStart) {
		m_Variable = var;
		m_StartIndex = start;
		m_EndIndex = end;
		m_RelativeStartIndex = relStart;
	}

	SubstitutionImpl(String var, au.edu.uq.rcc.nimrodg.api.utils.Substitution sub) {
		this(var, sub.startIndex(), sub.endIndex(), sub.relativeStartIndex());
	}

	private final String m_Variable;
	private final int m_StartIndex;
	private final int m_EndIndex;
	private final int m_RelativeStartIndex;

	@Override
	public int getStartIndex() {
		return m_StartIndex;
	}

	@Override
	public int getEndIndex() {
		return m_EndIndex;
	}

	@Override
	public int getLength() {
		return m_EndIndex - m_StartIndex;
	}

	// Start index relative to the end index of the previous substitution.
	// If no previous, this is the same as startIndex().
	@Override
	public int getRelativeStartIndex() {
		return m_RelativeStartIndex;
	}

	@Override
	public String getVariable() {
		return m_Variable;
	}

}
