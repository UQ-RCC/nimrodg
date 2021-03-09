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
package au.edu.uq.rcc.nimrodg.api.setup;

import java.util.Arrays;
import java.util.Objects;

public final class SchemaVersion implements Comparable<SchemaVersion> {
	public final int major;
	public final int minor;
	public final int patch;

	public static final SchemaVersion UNVERSIONED = SchemaVersion.of(0, 0, 0);

	private SchemaVersion(int major, int minor, int patch) {
		if((this.major = major) < 0) {
			throw new IllegalArgumentException("major");
		}

		if((this.minor = minor) < 0) {
			throw new IllegalArgumentException("minor");
		}

		if((this.patch = patch) < 0) {
			throw new IllegalArgumentException("patch");
		}
	}

	public static SchemaVersion of(int major, int minor, int patch) {
		return new SchemaVersion(major, minor, patch);
	}

	public boolean isCompatible(SchemaVersion sv) {
		Objects.requireNonNull(sv, "sv");

		if(major != sv.major) {
			return false;
		}

		if(minor > sv.minor) {
			return false;
		}

		return patch <= sv.patch;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		SchemaVersion that = (SchemaVersion)o;
		return major == that.major &&
			   minor == that.minor &&
			   patch == that.patch;
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch);
	}

	@Override
	public int compareTo(SchemaVersion sv) {
		int v;

		if((v  = Integer.compare(major, sv.major)) != 0) {
			return v;
		}

		if((v = Integer.compare(minor, sv.minor)) != 0) {
			return v;
		}

		return Integer.compare(patch, sv.patch);
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}

	public static SchemaVersion parse(String s) throws IllegalArgumentException {
		Objects.requireNonNull(s, "s");

		int[] elems = Arrays.stream(s.split("\\.", 3))
				.mapToInt(Integer::parseUnsignedInt)
				.toArray();

		if(elems.length != 3) {
			throw new IllegalArgumentException("A version must have three components");
		}

		return SchemaVersion.of(elems[0], elems[1], elems[2]);
	}
}
