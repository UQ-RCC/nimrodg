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
package au.edu.uq.rcc.nimrodg.api;

import java.util.Objects;

public final class MachinePair {
	private final String system;
	private final String machine;

	public static MachinePair of(String system, String machine) {
		if(system == null || machine == null) {
			throw new IllegalArgumentException();
		}

		return new MachinePair(system, machine);
	}

	private MachinePair(String system, String machine) {
		this.system = system;
		this.machine = machine;
	}

	public String system() {
		return system;
	}

	public String machine() {
		return machine;
	}

	@Override
	public String toString() {
		return String.format("%s,%s", system, machine);
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		MachinePair that = (MachinePair)o;
		return Objects.equals(system, that.system) &&
				Objects.equals(machine, that.machine);
	}

	@Override
	public int hashCode() {
		return Objects.hash(system, machine);
	}
}
