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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {

	/* HH[:MM[:SS]] */
	private static final Pattern WALLTIME_PATTERN1 = Pattern.compile("^(\\d+)(?::(\\d+)(?::(\\d+)(?::(\\d+))?)?)?$");
	/* [Hh][Mm][Ss]*/
	private static final Pattern WALLTIME_PATTERN2 = Pattern.compile("^(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$");

	private static final Pattern MEMORY_PATTERN = Pattern.compile("^(\\d+)([EPTGMK](?:i?[bB])?|[bB])?$");

	/**
	 * Convert a hex character to a hex digit.
	 */
	private static byte toHex(Integer cp) {
		if(cp == null) {
			return -1;
		} else if(cp >= '0' && cp <= '9') {
			return (byte)(cp - 48);
		} else if(cp >= 'A' && cp <= 'F') {
			return (byte)(cp - 55);
		} else if(cp >= 'a' && cp <= 'f') {
			return (byte)(cp - 87);
		} else {
			return -1;
		}
	}

	/**
	 * Convert an octal character to an octal digit.
	 */
	private static byte toOctal(Integer cp) {
		if(cp == null) {
			return -1;
		} else if(cp >= '0' && cp <= '7') {
			return (byte)(cp - 48);
		} else {
			return -1;
		}
	}

	/**
	 * Is the given code point a digit? [0-9]
	 */
	private static boolean isIdentifierDigit(int cp) {
		return cp >= '0' && cp <= '9';
	}

	/**
	 * Is the given code point a nondigit? [a-zA-Z]
	 */
	private static boolean isIdentifierNonDigit(int cp) {
		return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || cp == '_';
	}

	/**
	 * Drain at most pair of hex digits from a queue of code points.
	 *
	 * @param d The queue of code points.
	 * @param out The StringBuilder to receive the decoded hex.
	 * @return true, if hex digits were drained. Otherwise, false.
	 * @throws EscapeException
	 */
	private static boolean drainHexPair(Queue<Integer> d, StringBuilder out) throws EscapeException {
		int p1 = toHex(d.peek());
		if(p1 < 0) {
			return false;
		}
		d.poll();

		int p2 = toHex(d.peek());

		/* Handle "X" = 0x0X and "XY" = 0xXY */
		if(p2 < 0) {
			out.append((char)p1);
		} else {
			d.poll();
			out.append((char)(p1 << 4 | p2));
		}

		return true;
	}

	/**
	 * Drain a sequence of hex pairs from a queue of code points.
	 *
	 * @param d The queue of code points.
	 * @param out The StringBuilder to receive the decoded hex.
	 * @return The number of hex pairs drained.
	 * @throws EscapeException
	 */
	private static int drainHexSequence(Queue<Integer> d, StringBuilder out) throws EscapeException {
		int num = 0;
		while(drainHexPair(d, out)) {
			++num;
		}

		return num;
	}

	private static boolean drainOctal(Queue<Integer> d, StringBuilder out) throws EscapeException {
		int val = 0, i;
		for(i = 0; i < 3; ++i) {
			int p = toOctal(d.peek());
			if(p < 0) {
				break;
			}
			d.poll();

			val <<= 3;
			val |= p;
		}

		if(i == 0) {
			return false;
		}

		out.append((char)val);
		return true;
	}

	private static String drainIdentifier(Queue<Integer> d) throws SubstitutionException {
		StringBuilder sb = new StringBuilder();

		Integer c = d.peek();
		if(c == null) {
			// Will never happen, hopefully
			throw new IllegalStateException();
		}

		if(!isIdentifierNonDigit(c)) {
			throw new SubstitutionException("Identifier cannot begin with a digit");
		}

		sb.appendCodePoint(c);
		d.poll();

		for(;;) {
			c = d.peek();
			if(c == null) {
				break;
			} else if(!isIdentifierDigit(c) && !isIdentifierNonDigit(c)) {
				break;
			} else {
				sb.appendCodePoint(c);
				d.poll();
			}
		}

		return sb.toString();
	}

	/**
	 * Un-escape a C-string. Trigraphs and universal character codes are <b>NOT</b> supported.
	 *
	 * @param s The escaped string.
	 * @return An un-escaped string.
	 * @throws EscapeException - If the escaped string is invalid.
	 */
	public static String unescape(String s) throws EscapeException {
		StringBuilder sb = new StringBuilder();

		ArrayDeque<Integer> d = new ArrayDeque<>(s.length());
		s.codePoints().forEach(d::add);

		boolean escaped = false;
		while(!d.isEmpty()) {
			int c = d.poll();

			if(escaped) {
				if(c == '\\' || c == '"' || c == '\'') {
					sb.appendCodePoint(c);
					escaped = false;
				} else if(c == 'x') {
					if(drainHexSequence(d, sb) == 0) {
						throw new EscapeException("Empty hex escape sequence");
					}
					escaped = false;
				} else if(c >= '0' && c <= '9') {
					d.addFirst(c);
					if(!drainOctal(d, sb)) {
						throw new EscapeException("Empty octal escape sequence");
					}
					escaped = false;
				} else if(c == 'a') {
					sb.append((char)0x07);
					escaped = false;
				} else if(c == 'b') {
					sb.append((char)0x08);
					escaped = false;
				} else if(c == 'f') {
					sb.append('\f');
					escaped = false;
				} else if(c == 'n') {
					sb.append('\n');
					escaped = false;
				} else if(c == 'r') {
					sb.append('\r');
					escaped = false;
				} else if(c == 't') {
					sb.append('\t');
					escaped = false;
				} else if(c == 'v') {
					sb.append((char)0x0B);
					escaped = false;
				} else {
					throw new EscapeException("Unknown escape sequence '%c'", (char)c);
				}
			} else {
				if(c == '\\') {
					escaped = true;
				} else {
					sb.appendCodePoint(c);
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Scan a string for Nimrod/G substitutions.
	 *
	 * @param s The string.
	 * @return A list of Nimrod/G substitutions.
	 * @throws au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException
	 */
	public static List<Substitution> findSubstitutions(String s) throws SubstitutionException {
		/*
		Tried doing using regex, couldn't report errors:
		\$(?:([a-zA-Z][a-zA-Z0-9]*)|{([a-zA-Z][a-zA-Z0-9]*)})
		 */
		ArrayDeque<Integer> d = new ArrayDeque<>(s.length());
		s.codePoints().forEach(d::add);

		List<Substitution> subs = new ArrayList<>();

		boolean escaped = false;
		String name = null;

		// int mode:
		// 0 = Not in substitution
		// 1 = Found '$', looking for [{a-zA-Z]
		//   if '{', braceFlag = true
		// 2 = Read identifier, looking for '}' if braceFlag
		boolean braceFlag = false;
		for(int pos = 0, mode = 0, start = 0, prevEnd = 0;;) {
			Integer c = d.peek();

			if(escaped) {
				escaped = false;
				d.poll();
				continue;
			}

			if(mode == 2) {
				if(braceFlag) {
					if(c == null || c != '}') {
						throw new SubstitutionException("Unterminated closing brace");
					}
					braceFlag = false;
					d.poll();
					++pos;
					subs.add(new Substitution(name, start, pos, start - prevEnd));
					mode = 0;
					prevEnd = pos;
				} else {
					subs.add(new Substitution(name, start, pos, start - prevEnd));
					mode = 0;
					prevEnd = pos;
				}
			} else if(mode == 1) {
				if(c == null) {
					throw new SubstitutionException("Unexpected EOL");
				} else if(c == '{') {
					if(braceFlag) {
						throw new SubstitutionException("Can't have nested braces");
					}
					++pos;
					braceFlag = true;
					d.poll();
				} else {
					name = drainIdentifier(d);
					pos += name.length();
					mode = 2;
				}
			} else if(mode == 0) {
				if(c == null) {
					break;
				} else if(c == '\\') {
					escaped = true;
				} else if(c == '$') {
					start = pos;
					mode = 1;
				}
				d.poll();
				++pos;
			}
		}

		return subs;
	}

	public static String applySubstitutions(String s, List<Substitution> subs, Map<String, String> vals) {
		if(s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		int start = 0;
		for(Substitution sub : subs) {
			sb.append(s, start, start + sub.relativeStartIndex());
			sb.append(vals.get(sub.variable()));
			start = sub.startIndex() + sub.length();
		}

		sb.append(s, start, s.length());
		return sb.toString();
	}

	public static long parseMemory(String s) throws IllegalArgumentException {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		Matcher m = MEMORY_PATTERN.matcher(s);
		if(!m.matches()) {
			throw new IllegalArgumentException(String.format("Malformed memory string. Must match %s", MEMORY_PATTERN.toString()));
		}

		long val = Integer.parseUnsignedInt(m.group(1));
		String _units = m.group(2);
		if(_units == null) {
			/* Unspecified, assume bytes. */
			_units = "B";
		}

		char[] units = _units.toCharArray();
		long scalar;
		boolean isBits;

		if(units[0] == 'B') {
			scalar = 1;
			isBits = false;
		} else if(units[0] == 'b') {
			scalar = 1;
			isBits = true;
		} else {
			char scale = units[0];
			long x;
			if(units[1] == 'i') {
				x = 1024L;
				isBits = units[2] == 'b';
			} else {
				x = 1000L;
				isBits = units[1] == 'b';
			}

			if(scale == 'k' || scale == 'K') {
				scalar = x;
			} else if(scale == 'm' || scale == 'M') {
				scalar = x * x;
			} else if(scale == 'g' || scale == 'G') {
				scalar = x * x * x;
			} else if(scale == 't' || scale == 'T') {
				scalar = x * x * x * x;
			} else if(scale == 'p' || scale == 'P') {
				scalar = x * x * x * x * x;
			} else if(scale == 'e' || scale == 'E') {
				scalar = x * x * x * x * x * x;
			} else {
				throw new IllegalArgumentException("Unknown scale, this is a regex bug.");
			}
		}

		val *= scalar;
		if(isBits) {
			/* Bits, round up to nearest byte. */
			return (val / 8) + ((val % 8 > 0) ? 1 : 0);
		} else {
			/* Bytes, we're done here. */
			return val;
		}
	}

	private static long processWalltimeMatcher(Matcher m) {
		String day = m.group(1);
		String hour = m.group(2);
		String minute = m.group(3);
		String second = m.group(4);

		long seconds = 0;

		if(day != null) {
			seconds += Long.parseUnsignedLong(day) * 86400L;
		}

		if(hour != null) {
			seconds += Long.parseUnsignedLong(hour) * 3600L;
		}

		if(minute != null) {
			seconds += Long.parseUnsignedLong(minute) * 60L;
		}

		if(second != null) {
			seconds += Long.parseUnsignedLong(second);
		}
		return seconds;
	}

	public static long parseWalltime(String s) throws IllegalArgumentException {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		Matcher m = WALLTIME_PATTERN1.matcher(s);
		if(m.matches()) {
			return processWalltimeMatcher(m);
		}

		m = WALLTIME_PATTERN2.matcher(s);
		if(m.matches()) {
			return processWalltimeMatcher(m);
		}

		throw new IllegalArgumentException();
	}
}
