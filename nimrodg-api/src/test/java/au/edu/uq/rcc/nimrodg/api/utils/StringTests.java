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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import au.edu.uq.rcc.nimrodg.api.Substitution;
import junit.framework.Assert;
import org.junit.Test;

public class StringTests {

	@Test
	public void escapeHexSingleTest() throws EscapeException {
		Assert.assertEquals(0x04, StringUtils.unescape("\\x4").getBytes(StandardCharsets.US_ASCII)[0]);
	}

	@Test(expected = EscapeException.class)
	public void escapeHexEmptyTest() throws EscapeException {
		StringUtils.unescape("\\x");
	}

	@Test
	public void escapeHexTest() throws EscapeException {
		Assert.assertEquals("Hello, World", StringUtils.unescape("\\x48656C6C6F2C20576F726C64"));
	}

	@Test
	public void escapeOctalTest() throws EscapeException {
		Assert.assertEquals("Hello, World", StringUtils.unescape("\\110\\145\\154\\154\\157\\54\\40\\127\\157\\162\\154\\144"));
	}

	@Test(expected = SubstitutionException.class)
	public void substStartsWithDigitTest() throws SubstitutionException {
		StringUtils.findSubstitutions("$0a");
	}

	@Test(expected = SubstitutionException.class)
	public void substEmptyTest() throws SubstitutionException {
		StringUtils.findSubstitutions("$");
	}

	@Test
	public void substNoTest() throws SubstitutionException {
		String s = "Yo ho ho";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals(s, ps);
	}

	@Test
	public void substSimpleTest() throws SubstitutionException {
		String s = "$x";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello, World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals("Hello, World", ps);
	}

	@Test
	public void substSimpleBraceTest() throws SubstitutionException {
		String s = "${x}";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello, World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals("Hello, World", ps);
	}

	@Test
	public void substLeadingTest() throws SubstitutionException {
		String s = "START: $x, ${y}";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals("START: Hello, World", ps);
	}

	@Test
	public void substTrailingTest() throws SubstitutionException {
		String s = "$x, ${y} :END";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals("Hello, World :END", ps);
	}

	@Test
	public void substLeadingTrailingTest() throws SubstitutionException {
		String s = "START: $x, ${y} :END";
		List<Substitution> subs = StringUtils.findSubstitutions(s);

		Map<String, String> vals = new HashMap<>();
		vals.put("x", "Hello");
		vals.put("y", "World");

		String ps = StringUtils.applySubstitutions(s, subs, vals);
		Assert.assertEquals("START: Hello, World :END", ps);
	}

	@Test
	public void identifierTests() {
		for(int i = 0; i < 10; ++i) {
			char c = Character.forDigit(i, 10);
			Assert.assertTrue(StringUtils.isIdentifierDigit(c));
			Assert.assertFalse(StringUtils.isIdentifierNonDigit(c));
		}

		for(int i = 0; i < 26; ++i) {
			int upper = 'A' + i;
			int lower = 'a' + i;

			Assert.assertTrue(StringUtils.isIdentifierNonDigit(upper));
			Assert.assertTrue(StringUtils.isIdentifierNonDigit(lower));

			Assert.assertFalse(StringUtils.isIdentifierDigit(upper));
			Assert.assertFalse(StringUtils.isIdentifierDigit(lower));
		}

		Assert.assertTrue(StringUtils.isIdentifier("abc123"));
		Assert.assertTrue(StringUtils.isIdentifier("abcdef"));
		Assert.assertTrue(StringUtils.isIdentifier("a"));
		Assert.assertFalse(StringUtils.isIdentifier("0"));
		Assert.assertFalse(StringUtils.isIdentifier(""));
		Assert.assertFalse(StringUtils.isIdentifier("0asdf"));
	}

	@Test
	public void walltimeParseTest() {
		Assert.assertEquals(12, StringUtils.parseWalltime("12"));
		Assert.assertEquals(12 * 60 + 34, StringUtils.parseWalltime("12:34"));
		Assert.assertEquals(12 * 3600 + 34 * 60 + 56, StringUtils.parseWalltime("12:34:56"));

		Assert.assertEquals(12 * 86400, StringUtils.parseWalltime("12d"));
		Assert.assertEquals(12 * 86400 + 34 * 3600, StringUtils.parseWalltime("12d34h"));
		Assert.assertEquals(12 * 86400 + 34 * 3600 + 56 * 60, StringUtils.parseWalltime("12d34h56m"));
		Assert.assertEquals(12 * 86400 + 34 * 3600 + 56 * 60 + 78, StringUtils.parseWalltime("12d34h56m78s"));

		Assert.assertEquals(34 * 3600, StringUtils.parseWalltime("34h"));
		Assert.assertEquals(56 * 60, StringUtils.parseWalltime("56m"));
		Assert.assertEquals(78, StringUtils.parseWalltime("78s"));

		Assert.assertEquals(34 * 3600 + 56 * 60, StringUtils.parseWalltime("34h56m"));
		Assert.assertEquals(34 * 3600 + 78, StringUtils.parseWalltime("34h78s"));
	}

	@Test
	public void memoryParseTest() {
		Assert.assertEquals(1L, StringUtils.parseMemory("1"));
		Assert.assertEquals(1L, StringUtils.parseMemory("1b"));
		Assert.assertEquals(1L, StringUtils.parseMemory("1B"));
		Assert.assertEquals(125L, StringUtils.parseMemory("1Kb"));
		Assert.assertEquals(1000L, StringUtils.parseMemory("1KB"));
		Assert.assertEquals(128L, StringUtils.parseMemory("1Kib"));
		Assert.assertEquals(1024L, StringUtils.parseMemory("1KiB"));
		Assert.assertEquals(125000L, StringUtils.parseMemory("1Mb"));
		Assert.assertEquals(1000000L, StringUtils.parseMemory("1MB"));
		Assert.assertEquals(131072L, StringUtils.parseMemory("1Mib"));
		Assert.assertEquals(1048576L, StringUtils.parseMemory("1MiB"));
		Assert.assertEquals(125000000L, StringUtils.parseMemory("1Gb"));
		Assert.assertEquals(1000000000L, StringUtils.parseMemory("1GB"));
		Assert.assertEquals(134217728L, StringUtils.parseMemory("1Gib"));
		Assert.assertEquals(1073741824L, StringUtils.parseMemory("1GiB"));
		Assert.assertEquals(125000000000L, StringUtils.parseMemory("1Tb"));
		Assert.assertEquals(1000000000000L, StringUtils.parseMemory("1TB"));
		Assert.assertEquals(137438953472L, StringUtils.parseMemory("1Tib"));
		Assert.assertEquals(1099511627776L, StringUtils.parseMemory("1TiB"));
		Assert.assertEquals(125000000000000L, StringUtils.parseMemory("1Pb"));
		Assert.assertEquals(1000000000000000L, StringUtils.parseMemory("1PB"));
		Assert.assertEquals(140737488355328L, StringUtils.parseMemory("1Pib"));
		Assert.assertEquals(1125899906842624L, StringUtils.parseMemory("1PiB"));
		Assert.assertEquals(125000000000000000L, StringUtils.parseMemory("1Eb"));
		Assert.assertEquals(1000000000000000000L, StringUtils.parseMemory("1EB"));
		Assert.assertEquals(144115188075855872L, StringUtils.parseMemory("1Eib"));
		Assert.assertEquals(1152921504606846976L, StringUtils.parseMemory("1EiB"));
	}

	@Test
	public void queueParseTest() {
		Assert.assertEquals(Map.entry(Optional.of("workq"), Optional.empty()), StringUtils.parseQueue("workq"));
		Assert.assertEquals(Map.entry(Optional.empty(), Optional.of("tinmgr2.ibo")), StringUtils.parseQueue("@tinmgr2.ibo"));
		Assert.assertEquals(Map.entry(Optional.of("workq"), Optional.of("tinmgmr2.ibo")), StringUtils.parseQueue("workq@tinmgmr2.ibo"));
	}
}
