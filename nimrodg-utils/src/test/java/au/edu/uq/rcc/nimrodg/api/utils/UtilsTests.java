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

import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class UtilsTests {
	public static final List<String> TEST_STRINGS = List.of("a", "b", "c");
	public static final List<String> TEST_EMPTY_LIST = List.of();

	@Test
	public void selectRandomTest() {
		Random rnd = new Random(0);
		Random rnd2 = new Random(0);

		for(int i = 0; i < 10; ++i) {
			String expected = TEST_STRINGS.get(rnd.nextInt(TEST_STRINGS.size()));
			Optional<String> actual = NimrodUtils.selectRandomFromContainer(TEST_STRINGS, rnd2);
			Assert.assertTrue(actual.isPresent());
			Assert.assertEquals(expected, actual.get());
		}

		rnd.setSeed(0);
		rnd2.setSeed(0);

		Assert.assertFalse(NimrodUtils.selectRandomFromContainer(TEST_EMPTY_LIST).isPresent());
		Assert.assertFalse(NimrodUtils.selectRandomFromContainer(TEST_EMPTY_LIST, rnd2).isPresent());
	}

	@Test
	public void coalesceTest() {
		Assert.assertEquals("a", NimrodUtils.coalesce("a", null, null));
		Assert.assertEquals("b", NimrodUtils.coalesce(null, "b", null));
		Assert.assertEquals("c", NimrodUtils.coalesce(null, null, "c"));
		Assert.assertNull(NimrodUtils.coalesce(null, null, null));
	}

	@Test
	public void getOrAddLazyTest() {
		Map<String, String> map = new HashMap<>(6);
		map.put("a", "A");
		map.put("b", "B");

		/* Try doing this with a standard int, I dare you. */
		AtomicInteger invokeCount = new AtomicInteger(0);

		for(char i = 'a'; i <= 'f'; ++i) {
			String key = new String(new char[]{i});
			NimrodUtils.getOrAddLazy(map, key, k -> {
				invokeCount.getAndIncrement();
				return k.toUpperCase(Locale.ENGLISH);
			});
		}

		Assert.assertEquals(4, invokeCount.get());
		Assert.assertEquals(Map.of(
				"a", "A", "b", "B", "c", "C",
				"d", "D", "e", "E", "f", "F"
		), map);
	}

	@Test
	public void mapToParentTest() {
		Map<String, List<String>> m1 = NimrodUtils.mapToParent(
				List.of("A", "B", "C", "D", "E", "F"),
				v -> v.toLowerCase(Locale.ENGLISH)
		);

		Assert.assertEquals(Map.of(
				"a", List.of("A"), "b", List.of("B"), "c", List.of("C"),
				"d", List.of("D"), "e", List.of("E"), "f", List.of("F")
		), m1);
	}
}
