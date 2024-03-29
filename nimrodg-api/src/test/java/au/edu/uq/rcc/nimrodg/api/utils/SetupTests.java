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

import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SetupTests {
	@Test
	public void schemaVersionTests() {
		SchemaVersion v100 = SchemaVersion.of(1, 0, 0);
		SchemaVersion v100_2 = SchemaVersion.of(1, 0, 0);
		SchemaVersion v110 = SchemaVersion.of(1, 1, 0);
		SchemaVersion v201 = SchemaVersion.of(2, 0, 1);
		SchemaVersion v300 = SchemaVersion.of(3, 0, 0);
		SchemaVersion v301 = SchemaVersion.of(3, 0, 1);

		Assertions.assertEquals(v100, v100_2);
		Assertions.assertTrue(v100.isCompatible(v110));
		Assertions.assertFalse(v110.isCompatible(v100));
		Assertions.assertEquals(v100.compareTo(v100_2), 0);
		Assertions.assertTrue(v100.compareTo(v110) < 0);
		Assertions.assertTrue(v110.compareTo(v100) > 0);
		Assertions.assertFalse(v100.isCompatible(v201));
		Assertions.assertFalse(v201.isCompatible(v100));
		Assertions.assertFalse(v201.isCompatible(v110));
		Assertions.assertTrue(v100.compareTo(v201) < 0);
		Assertions.assertTrue(v201.compareTo(v100) > 0);

		Assertions.assertTrue(v300.compareTo(v301) < 0);
		Assertions.assertFalse(v300.compareTo(v301) > 0);
		Assertions.assertTrue(v300.isCompatible(v301));
		Assertions.assertFalse(v301.isCompatible(v300));
	}
}
