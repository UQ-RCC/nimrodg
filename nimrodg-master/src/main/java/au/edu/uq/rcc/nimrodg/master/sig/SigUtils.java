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
package au.edu.uq.rcc.nimrodg.master.sig;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import com.rabbitmq.client.AMQP;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.NullDigest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SigUtils {
	public static final String DEFAULT_APPID = "nimrod";
	public static final String DEFAULT_ALGORITHM = "NIM1-HMAC-SHA256";

	public interface AlgoInfo {
		Digest getDigest();

		default Mac getMac() {
			return new HMac(getDigest());
		}
	}

	public static final Map<String, AlgoInfo> ALGORITHMS = Map.of(
			"NIM1-HMAC-NULL", new AlgoInfo() {
				@Override
				public Digest getDigest() {
					return new MoreNullDigest();
				}

				@Override
				public Mac getMac() {
					return new NullMac();
				}
			},
			"NIM1-HMAC-SHA224", SHA224Digest::new,
			"NIM1-HMAC-SHA256", SHA256Digest::new,
			"NIM1-HMAC-SHA384", SHA384Digest::new,
			"NIM1-HMAC-SHA512", SHA512Digest::new
	);

	private static class Procs {
		public final Function<AMQP.BasicProperties, String> getString;
		public final BiFunction<AMQP.BasicProperties, AMQP.BasicProperties.Builder, AMQP.BasicProperties.Builder> apply;

		public Procs(Function<AMQP.BasicProperties, String> getString, BiFunction<AMQP.BasicProperties, AMQP.BasicProperties.Builder, AMQP.BasicProperties.Builder> apply) {
			this.getString = getString;
			this.apply = apply;
		}
	}

	private static final Map<String, Procs> ALLOWED_PROPERTIES = new TreeMap<>() {{
		put("app-id", new Procs(
				AMQP.BasicProperties::getAppId,
				(p, b) -> b.appId(p.getAppId())
		));
		put("cluster-id", new Procs(
				AMQP.BasicProperties::getClusterId,
				(p, b) -> b.clusterId(p.getClusterId())
		));
		put("content-encoding", new Procs(
				AMQP.BasicProperties::getContentEncoding,
				(p, b) -> b.contentEncoding(p.getContentEncoding())
		));
		put("content-type", new Procs(
				AMQP.BasicProperties::getContentType,
				(p, b) -> b.contentType(p.getContentType())
		));
		put("correlation-id", new Procs(
				AMQP.BasicProperties::getCorrelationId,
				(p, b) -> b.correlationId(p.getCorrelationId())
		));
		put("delivery-mode", new Procs(
				p -> Optional.ofNullable(p.getDeliveryMode()).map(String::valueOf).orElse(null),
				(p, b) -> b.deliveryMode(p.getDeliveryMode())
		));
		put("expiration", new Procs(
				AMQP.BasicProperties::getExpiration,
				(p, b) -> b.expiration(p.getExpiration())
		));
		put("message-id", new Procs(
				AMQP.BasicProperties::getMessageId,
				(p, b) -> b.messageId(p.getMessageId())
		));
		put("priority", new Procs(
				p -> Optional.ofNullable(p.getPriority()).map(String::valueOf).orElse(null),
				(p, b) -> b.priority(p.getPriority())
		));
		put("reply-to", new Procs(
				AMQP.BasicProperties::getReplyTo,
				(p, b) -> b.replyTo(p.getReplyTo())
		));
		put("timestamp", new Procs(
				p -> String.valueOf(p.getTimestamp().toInstant().getEpochSecond()),
				(p, b) -> b.timestamp(p.getTimestamp())
		));
		put("type", new Procs(
				AMQP.BasicProperties::getType,
				(p, b) -> b.type(p.getType())
		));
		put("user-id", new Procs(
				AMQP.BasicProperties::getUserId,
				(p, b) -> b.userId(p.getUserId())
		));
	}};

	/**
	 * Build an AMQP.BasicProperties that only contains things to verify.
	 *
	 * @param hdr   The authorization header.
	 * @param props The basic properties.
	 * @return An AMQP.BasicProperties that only contains things to verify.
	 */
	public static AMQP.BasicProperties buildBasicProperties(AuthHeader hdr, AMQP.BasicProperties props) {
		AMQP.BasicProperties.Builder b = new AMQP.BasicProperties.Builder();

		for(String s : hdr.signedProperties) {
			Procs procs = ALLOWED_PROPERTIES.get(s);
			if(procs == null) {
				/* Invalid property. */
				return null;
			}
			procs.apply.apply(props, b);
		}

		/* No headers, we're done here. */
		Map<String, Object> headers = props.getHeaders();
		if(headers == null)
			return b.build();

		/* Lowercase all the keys. */
		headers = headers.entrySet().stream().collect(Collectors.toMap(
				e -> e.getKey().toLowerCase(Locale.US),
				Map.Entry::getValue
		));

		Map<String, Object> newHeaders = new HashMap<>(headers.size());
		for(String s : hdr.signedHeaders) {
			Object val = headers.get(s);
			if(val == null) {
				/* Missing header. */
				return null;
			}
			newHeaders.put(s, val);
		}

		return b.headers(newHeaders).build();
	}

	/* KeyParameter copies the key, so it's safe for both key and out to point to the same buffer. */
	private static byte[] hmacCascade(Mac mac, byte[] key, byte[] data, byte[] out) {
		mac.reset();
		mac.init(new KeyParameter(key));
		mac.update(data, 0, data.length);
		mac.doFinal(out, 0);
		return out;
	}

	/* HMAC(HMAC("NIM1" + secret, "YYYYMMDDTHHMMSSZ"), app_id) */
	private static byte[] buildSigningKey(Mac mac, String secretKey, String timestamp, long nonce, String appId, byte[] out) {
		hmacCascade(mac, ("NIM1" + secretKey).getBytes(StandardCharsets.UTF_8), timestamp.getBytes(StandardCharsets.UTF_8), out);
		hmacCascade(mac, out, Long.toUnsignedString(nonce).getBytes(StandardCharsets.UTF_8), out);
		hmacCascade(mac, out, appId.getBytes(StandardCharsets.UTF_8), out);
		return out;
	}

	private static TreeMap<String, String> buildProperties(AMQP.BasicProperties props) {
		TreeMap<String, String> m = new TreeMap<>();

		ALLOWED_PROPERTIES.forEach((k, v) -> {
			String val = v.getString.apply(props);
			if(val == null) {
				return;
			}

			m.put(k.toLowerCase(Locale.US), val);
		});

		return m;
	}

	private static TreeMap<String, String> buildHeaders(AMQP.BasicProperties props) {
		Map<String, Object> headers = props.getHeaders();
		if(headers == null) {
			return new TreeMap<>();
		}

		TreeMap<String, String> m = new TreeMap<>();
		headers.forEach((k, v) -> m.put(k.toLowerCase(Locale.US), v.toString()));
		return m;
	}

	private static StringBuilder writeHex(StringBuilder sb, byte[] payload) {
		final String characters = "0123456789abcdef";
		for(byte b : payload) {
			sb.append(characters.charAt((b >> 4) & 0x0F));
			sb.append(characters.charAt(b & 0x0F));
		}

		return sb;
	}

	public static AuthHeader buildAuthHeader(String algorithm, String accessKey, String secretKey, Instant t, long nonce, byte[] payload, AMQP.BasicProperties props) throws NoSuchAlgorithmException {
		final AlgoInfo algo = ALGORITHMS.get(algorithm);
		if(algo == null) {
			throw new NoSuchAlgorithmException();
		}

		final Digest md = algo.getDigest();
		final StringBuilder sb = new StringBuilder();
		final Mac hmac = algo.getMac();
		final byte[] buf = new byte[hmac.getMacSize()];
		final byte[] buf2 = new byte[md.getDigestSize()];

		/* Need to do this so ChronoField's will work. */
		ZonedDateTime zdt = t.atZone(ZoneOffset.UTC);

		String timestamp = String.format("%04d%02d%02dT%02d%02d%02dZ",
				zdt.get(ChronoField.YEAR),
				zdt.get(ChronoField.MONTH_OF_YEAR),
				zdt.get(ChronoField.DAY_OF_MONTH),
				zdt.get(ChronoField.HOUR_OF_DAY),
				zdt.get(ChronoField.MINUTE_OF_HOUR),
				zdt.get(ChronoField.SECOND_OF_MINUTE)
		);

		String appid = props.getAppId();
		if(appid == null) {
			appid = DEFAULT_APPID;
		}

		buildSigningKey(hmac, secretKey, timestamp, nonce, appid, buf);

		TreeMap<String, String> properties = buildProperties(props);
		TreeMap<String, String> headers = buildHeaders(props);

		/* Format the canonical request. */
		String canonicalRequest;
		{
			sb.setLength(0);
			properties.forEach((k, v) -> {
				sb.append(k);
				sb.append(":");
				sb.append(v);
				sb.append("\n");
			});
			sb.append("\n");

			for(String s : properties.keySet()) {
				sb.append(s);
				sb.append(";");
			}
			sb.setLength(sb.length() - 1);
			sb.append("\n");

			headers.forEach((k, v) -> {
				sb.append(k);
				sb.append(":");
				sb.append(v);
				sb.append("\n");
			});
			sb.append("\n");

			for(String s : headers.keySet()) {
				sb.append(s);
				sb.append(";");
			}
			sb.setLength(sb.length() - 1);
			sb.append("\n");

			md.reset();
			md.update(payload, 0, payload.length);
			md.doFinal(buf2, 0);
			writeHex(sb, buf2);
			canonicalRequest = sb.toString();
		}

		/* Format the "string to sign". */
		String sts;
		{
			sb.setLength(0);
			/* Algo */
			sb.append(algorithm);
			sb.append("\n");
			/* Timestamp */
			sb.append(timestamp);
			sb.append("\n");
			/* Scope */
			sb.append(timestamp);
			sb.append("/");
			sb.append(Long.toUnsignedString(nonce));
			sb.append("/");
			sb.append(appid);
			sb.append("\n");
			/* Signature */
			md.reset();

			byte[] cbytes = canonicalRequest.getBytes(StandardCharsets.UTF_8);
			md.update(cbytes, 0, cbytes.length);
			md.doFinal(buf2, 0);
			writeHex(sb, buf2);
			sts = sb.toString();
		}

		String credential;
		{
			sb.setLength(0);
			sb.append(accessKey);
			sb.append("/");
			sb.append(timestamp);
			sb.append("/");
			sb.append(Long.toUnsignedString(nonce));
			sb.append("/");
			sb.append(appid);
			credential = sb.toString();
		}

		hmacCascade(hmac, buf, sts.getBytes(StandardCharsets.UTF_8), buf);

		sb.setLength(0);
		return new AuthHeader(
				algorithm,
				credential,
				accessKey,
				timestamp,
				t,
				nonce,
				appid,
				properties.keySet(),
				headers.keySet(),
				writeHex(sb, buf).toString()
		);
	}

	public static boolean validateMessage(AMQP.BasicProperties props, AuthHeader hdr, AgentMessage msg) {
		return validateMessage(props, hdr, msg, DEFAULT_APPID);
	}

	public static boolean validateMessage(AMQP.BasicProperties props, AuthHeader hdr, AgentMessage msg, String appid) {
		Objects.requireNonNull(props, "props");
		Objects.requireNonNull(hdr, "hdr");
		Objects.requireNonNull(msg, "msg");
		Objects.requireNonNull(appid, "appid");

		/* Check timestamps. */
		if(props.getTimestamp() == null) {
			return false;
		}

		assert hdr.timestamp.getNano() == 0;
		if(!Objects.equals(props.getTimestamp().toInstant(), hdr.timestamp)) {
			return false;
		}

		if(msg.getTimestamp().getEpochSecond() != hdr.timestamp.getEpochSecond()) {
			return false;
		}

		/* Check appid. */
		if(props.getAppId() == null) {
			return false;
		}

		if(!hdr.appid.equals(props.getAppId())) {
			return false;
		}

		if(!appid.equals(hdr.appid)) {
			return false;
		}

		return true;
	}

	public static boolean verifySignature(AuthHeader hdr, String secretKey, AMQP.BasicProperties props, byte[] payload) throws NoSuchAlgorithmException {
		Objects.requireNonNull(hdr, "hdr");
		Objects.requireNonNull(props, "props");
		Objects.requireNonNull(payload, "payload");

		AMQP.BasicProperties newProps = SigUtils.buildBasicProperties(hdr, props);
		if(newProps == null) {
			/* Invalid/Missing signed properties or headers. */
			return false;
		}

		AuthHeader ourHdr = SigUtils.buildAuthHeader(hdr.algorithm, hdr.accessKey, secretKey, hdr.timestamp, hdr.nonce, payload, newProps);
		return ourHdr.equals(hdr);
	}

	public static String buildAccessKey(UUID agentUuid) {
		Objects.requireNonNull(agentUuid, "agentUuid");
		return agentUuid.toString().replace("-", "");
	}
}
