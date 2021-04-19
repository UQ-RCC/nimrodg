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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import au.edu.uq.rcc.nimrodg.api.Substitution;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringTests {

	@Test
	public void escapeHexSingleTest() throws EscapeException {
		Assertions.assertEquals(0x04, StringUtils.unescape("\\x4").getBytes(StandardCharsets.US_ASCII)[0]);
	}

	@Test
	public void escapeHexEmptyTest() {
		Assertions.assertThrows(EscapeException.class, () -> StringUtils.unescape("\\x"));
	}

	@Test
	public void escapeHexTest() throws EscapeException {
		Assertions.assertEquals("Hello, World", StringUtils.unescape("\\x48656C6C6F2C20576F726C64"));
	}

	@Test
	public void escapeOctalTest() throws EscapeException {
		Assertions.assertEquals("Hello, World", StringUtils.unescape("\\110\\145\\154\\154\\157\\54\\40\\127\\157\\162\\154\\144"));
	}

	@Test
	public void substStartsWithDigitTest() throws SubstitutionException {
		Assertions.assertThrows(SubstitutionException.class, () -> StringUtils.findSubstitutions("$0a"));
	}

	@Test
	public void substEmptyTest() throws SubstitutionException {
		Assertions.assertThrows(SubstitutionException.class, () -> StringUtils.findSubstitutions("$"));
	}

	@Test
	public void substNoTest() throws SubstitutionException {
		String s = "Yo ho ho";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals(s, ps);
	}

	@Test
	public void substSimpleTest() throws SubstitutionException {
		String s = "$x";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello, World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals("Hello, World", ps);
	}

	@Test
	public void substSimpleBraceTest() throws SubstitutionException {
		String s = "${x}";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello, World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals("Hello, World", ps);
	}

	@Test
	public void substLeadingTest() throws SubstitutionException {
		String s = "START: $x, ${y}";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals("START: Hello, World", ps);
	}

	@Test
	public void substTrailingTest() throws SubstitutionException {
		String s = "$x, ${y} :END";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals("Hello, World :END", ps);
	}

	@Test
	public void substLeadingTrailingTest() throws SubstitutionException {
		String s = "START: $x, ${y} :END";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assertions.assertEquals("START: Hello, World :END", ps);
	}

	@Test
	public void identifierTests() {
		for(int i = 0; i < 10; ++i) {
			char c = Character.forDigit(i, 10);
			Assertions.assertTrue(StringUtils.isIdentifierDigit(c));
			Assertions.assertFalse(StringUtils.isIdentifierNonDigit(c));
		}

		for(int i = 0; i < 26; ++i) {
			int upper = 'A' + i;
			int lower = 'a' + i;

			Assertions.assertTrue(StringUtils.isIdentifierNonDigit(upper));
			Assertions.assertTrue(StringUtils.isIdentifierNonDigit(lower));

			Assertions.assertFalse(StringUtils.isIdentifierDigit(upper));
			Assertions.assertFalse(StringUtils.isIdentifierDigit(lower));
		}

		Assertions.assertTrue(StringUtils.isIdentifier("abc123"));
		Assertions.assertTrue(StringUtils.isIdentifier("abcdef"));
		Assertions.assertTrue(StringUtils.isIdentifier("a"));
		Assertions.assertFalse(StringUtils.isIdentifier("0"));
		Assertions.assertFalse(StringUtils.isIdentifier(""));
		Assertions.assertFalse(StringUtils.isIdentifier("0asdf"));
	}

	@Test
	public void walltimeParseTest() {
		Assertions.assertEquals(12, StringUtils.parseWalltime("12"));
		Assertions.assertEquals(12 * 60 + 34, StringUtils.parseWalltime("12:34"));
		Assertions.assertEquals(12 * 3600 + 34 * 60 + 56, StringUtils.parseWalltime("12:34:56"));

		Assertions.assertEquals(12 * 86400, StringUtils.parseWalltime("12d"));
		Assertions.assertEquals(12 * 86400 + 34 * 3600, StringUtils.parseWalltime("12d34h"));
		Assertions.assertEquals(12 * 86400 + 34 * 3600 + 56 * 60, StringUtils.parseWalltime("12d34h56m"));
		Assertions.assertEquals(12 * 86400 + 34 * 3600 + 56 * 60 + 78, StringUtils.parseWalltime("12d34h56m78s"));

		Assertions.assertEquals(34 * 3600, StringUtils.parseWalltime("34h"));
		Assertions.assertEquals(56 * 60, StringUtils.parseWalltime("56m"));
		Assertions.assertEquals(78, StringUtils.parseWalltime("78s"));

		Assertions.assertEquals(34 * 3600 + 56 * 60, StringUtils.parseWalltime("34h56m"));
		Assertions.assertEquals(34 * 3600 + 78, StringUtils.parseWalltime("34h78s"));
	}

	@Test
	public void memoryParseTest() {
		Assertions.assertEquals(1L, StringUtils.parseMemory("1"));
		Assertions.assertEquals(1L, StringUtils.parseMemory("1b"));
		Assertions.assertEquals(1L, StringUtils.parseMemory("1B"));
		Assertions.assertEquals(125L, StringUtils.parseMemory("1Kb"));
		Assertions.assertEquals(1000L, StringUtils.parseMemory("1KB"));
		Assertions.assertEquals(128L, StringUtils.parseMemory("1Kib"));
		Assertions.assertEquals(1024L, StringUtils.parseMemory("1KiB"));
		Assertions.assertEquals(125000L, StringUtils.parseMemory("1Mb"));
		Assertions.assertEquals(1000000L, StringUtils.parseMemory("1MB"));
		Assertions.assertEquals(131072L, StringUtils.parseMemory("1Mib"));
		Assertions.assertEquals(1048576L, StringUtils.parseMemory("1MiB"));
		Assertions.assertEquals(125000000L, StringUtils.parseMemory("1Gb"));
		Assertions.assertEquals(1000000000L, StringUtils.parseMemory("1GB"));
		Assertions.assertEquals(134217728L, StringUtils.parseMemory("1Gib"));
		Assertions.assertEquals(1073741824L, StringUtils.parseMemory("1GiB"));
		Assertions.assertEquals(125000000000L, StringUtils.parseMemory("1Tb"));
		Assertions.assertEquals(1000000000000L, StringUtils.parseMemory("1TB"));
		Assertions.assertEquals(137438953472L, StringUtils.parseMemory("1Tib"));
		Assertions.assertEquals(1099511627776L, StringUtils.parseMemory("1TiB"));
		Assertions.assertEquals(125000000000000L, StringUtils.parseMemory("1Pb"));
		Assertions.assertEquals(1000000000000000L, StringUtils.parseMemory("1PB"));
		Assertions.assertEquals(140737488355328L, StringUtils.parseMemory("1Pib"));
		Assertions.assertEquals(1125899906842624L, StringUtils.parseMemory("1PiB"));
		Assertions.assertEquals(125000000000000000L, StringUtils.parseMemory("1Eb"));
		Assertions.assertEquals(1000000000000000000L, StringUtils.parseMemory("1EB"));
		Assertions.assertEquals(144115188075855872L, StringUtils.parseMemory("1Eib"));
		Assertions.assertEquals(1152921504606846976L, StringUtils.parseMemory("1EiB"));
	}

	@Test
	public void queueParseTest() {
		Assertions.assertEquals(Map.entry(Optional.of("workq"), Optional.empty()), StringUtils.parseQueue("workq"));
		Assertions.assertEquals(Map.entry(Optional.empty(), Optional.of("tinmgr2.ibo")), StringUtils.parseQueue("@tinmgr2.ibo"));
		Assertions.assertEquals(Map.entry(Optional.of("workq"), Optional.of("tinmgmr2.ibo")), StringUtils.parseQueue("workq@tinmgmr2.ibo"));
	}
}
