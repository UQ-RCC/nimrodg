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
package au.edu.uq.rcc.nimrodg.master.sig;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuthHeader {
	private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile("(?<algorithm>NIM1-[\\w-]+)\\s+Credential=(?<credential>(?<accesskey>[\\w]+)\\/(?<timestamp>(?<year>\\d{4})(?<month>\\d{2})(?<day>\\d{2})T(?<hour>\\d{2})(?<minute>\\d{2})(?<second>\\d{2})Z)\\/(?<nonce>\\d+)\\/(?<appid>[\\w]+)),\\s*SignedProperties=(?<properties>[\\w-;]+),\\s*SignedHeaders=(?<headers>[\\w-;]+),\\s+Signature=(?<signature>[a-z0-9]*)$");

	public final String algorithm;
	public final String credential;
	public final String accessKey;
	public final String timestampString;
	public final Instant timestamp;
	public final long nonce;
	public final String appid;

	/**
	 * The set of properties to verify. These are sorted by code-point.
	 */
	public final Set<String> signedProperties;
	/**
	 * The set of headers to verify. These are sorted by code-point.
	 */
	public final Set<String> signedHeaders;
	public final String signature;

	public final String header;

	/* This doesn't validate inputs, trust the caller to do it. */
	AuthHeader(String algorithm, String credential, String accessKey, String timestampString, Instant timestamp, long nonce, String appid, Set<String> signedProperties, Set<String> signedHeaders, String signature) {
		this.algorithm = algorithm;
		this.credential = credential;
		this.accessKey = accessKey;
		this.timestampString = timestampString;
		this.timestamp = timestamp;
		this.nonce = nonce;
		this.appid = appid;
		this.signedProperties = signedProperties;
		this.signedHeaders = signedHeaders;
		this.signature = signature;
		this.header = String.format("%s Credential=%s, SignedProperties=%s, SignedHeaders=%s, Signature=%s",
				algorithm,
				credential,
				String.join(";", signedProperties),
				String.join(";", signedHeaders),
				signature
		);
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		AuthHeader that = (AuthHeader)o;
		return header.equals(that.header);
	}

	@Override
	public int hashCode() {
		return header.hashCode();
	}

	@Override
	public String toString() {
		return header;
	}

	public static AuthHeader parse(String s) {
		Matcher m = AUTH_HEADER_PATTERN.matcher(s);
		if(!m.matches()) {
			return null;
		}

		/* Make sure properties and headers are sorted and have no duplicates. */
		String[] _props = m.group("properties").split(";");
		TreeSet<String> props = new TreeSet<>(Arrays.asList(_props));
		if(Arrays.compare(_props, props.stream().toArray(String[]::new)) != 0) {
			return null;
		}


		String[] _headers = m.group("headers").split(";");
		TreeSet<String> headers = new TreeSet<>(Arrays.asList(_headers));
		if(Arrays.compare(_headers, headers.stream().toArray(String[]::new)) != 0) {
			return null;
		}

		return new AuthHeader(
				m.group("algorithm"),
				m.group("credential"),
				m.group("accesskey"),
				m.group("timestamp"),
				OffsetDateTime.of(
						Integer.parseInt(m.group("year")),
						Integer.parseInt(m.group("month")),
						Integer.parseInt(m.group("day")),
						Integer.parseInt(m.group("hour")),
						Integer.parseInt(m.group("minute")),
						Integer.parseInt(m.group("second")),
						0,
						ZoneOffset.UTC
				).toInstant(),
				Long.parseUnsignedLong(m.group("nonce")),
				m.group("appid"),
				Collections.unmodifiableSet(props),
				Collections.unmodifiableSet(headers),
				m.group("signature")
		);
	}
}
