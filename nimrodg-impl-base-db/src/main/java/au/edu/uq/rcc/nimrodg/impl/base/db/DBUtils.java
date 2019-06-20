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

import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

public class DBUtils {

	public static Instant getInstant(ResultSet rs, String name) throws SQLException {
		OffsetDateTime odt = rs.getObject(name, OffsetDateTime.class);
		if(odt == null) {
			return null;
		}
		return odt.toInstant();
	}

	public static void setInstant(PreparedStatement ps, int index, Instant instant) throws SQLException {
		if(instant == null || instant.equals(Instant.MAX)) {
			ps.setObject(index, null);
		} else {
			ps.setObject(index, instant.atOffset(ZoneOffset.UTC));
		}
	}

	public static Instant getLongInstant(ResultSet rs, String name) throws SQLException {
		long l = rs.getLong(name);
		if(l == 0) {
			return null;
		}

		return Instant.ofEpochSecond(l);
	}

	public static void setLongInstant(PreparedStatement ps, int index, Instant instant) throws SQLException {
		if(instant == null || instant.equals(Instant.MAX)) {
			ps.setObject(index, null);
		} else {
			ps.setLong(index, instant.toEpochMilli() / 1000);
		}
	}

	public static void setTimestamp(PreparedStatement ps, int index, long ts) throws SQLException {
		if(ts < 0) {
			setInstant(ps, index, null);
		} else {
			setInstant(ps, index, Instant.ofEpochSecond(ts));
		}
	}

	public static JsonObject getJSONObject(ResultSet rs, String name) throws SQLException {
		String json = rs.getString(name);
		if(json == null) {
			return null;
		}

		try(JsonReader r = Json.createReader(new StringReader(json))) {
			return r.readObject();
		}
	}

	public static JsonArray getJSONArray(ResultSet rs, String name) throws SQLException {
		String json = rs.getString(name);
		if(json == null) {
			return null;
		}

		try(JsonReader r = Json.createReader(new StringReader(json))) {
			return r.readArray();
		}
	}

	public static Boolean getBooleanObject(ResultSet rs, String name) throws SQLException {
		boolean b = rs.getBoolean(name);
		if(rs.wasNull()) {
			return null;
		}

		return b;
	}

	@SafeVarargs
	public static <T> T coalesce(T... args) {
		for(int i = 0; i < args.length; ++i) {
			if(args[i] != null) {
				return args[i];
			}
		}

		return null;
	}

	public static NimrodURI mergeNimrodURI(NimrodURI oldUri, NimrodURI newUri) {
		if(oldUri == null) {
			return newUri;
		}

		if(newUri == null) {
			return oldUri;
		}

		return NimrodURI.create(
				coalesce(newUri.uri, oldUri.uri),
				coalesce(newUri.certPath, oldUri.certPath),
				coalesce(newUri.noVerifyPeer, oldUri.noVerifyPeer),
				coalesce(newUri.noVerifyHost, oldUri.noVerifyHost)
		);
	}

	public static TempConfig mergeConfig(TempConfig oldCfg, TempConfig newCfg) {
		if(oldCfg == null) {
			return newCfg;
		}

		if(newCfg == null) {
			return oldCfg;
		}

		return new TempConfig(
				coalesce(newCfg.workDir, oldCfg.workDir),
				coalesce(newCfg.storeDir, oldCfg.storeDir),
				mergeNimrodURI(newCfg.amqpUri, oldCfg.amqpUri),
				coalesce(newCfg.amqpRoutingKey, oldCfg.amqpRoutingKey),
				mergeNimrodURI(newCfg.txUri, oldCfg.txUri)
		);
	}

	public static NimrodURI getPrefixedNimrodUri(ResultSet rs, String prefix) throws SQLException {
		String suri = rs.getString(String.format("%suri", prefix));

		if(suri == null) {
			return null;
		}

		return NimrodURI.create(
				URI.create(suri),
				rs.getString(String.format("%scert_path", prefix)),
				DBUtils.getBooleanObject(rs, String.format("%sno_verify_peer", prefix)),
				DBUtils.getBooleanObject(rs, String.format("%sno_verify_host", prefix))
		);
	}

	public static void setNimrodUri(PreparedStatement ps, int start, NimrodURI uri) throws SQLException {
		if(uri == null) {
			for(int i = 0; i < 4; ++i) {
				ps.setObject(start + i, null);
			}

			return;
		}

		ps.setString(start, uri.uri == null ? null : uri.uri.toString());
		ps.setString(start + 1, uri.certPath);
		ps.setObject(start + 2, uri.noVerifyPeer);
		ps.setObject(start + 3, uri.noVerifyHost);
	}

	public static TempConfig configFromResultSet(ResultSet rs) throws SQLException {
		return new TempConfig(
				rs.getString("work_dir"),
				rs.getString("store_dir"),
				getPrefixedNimrodUri(rs, "amqp_"),
				rs.getString("amqp_routing_key"),
				getPrefixedNimrodUri(rs, "tx_")
		);
	}

	public static String jsonStringOrNull(JsonObject o, String name) {
		JsonValue v = o.get(name);
		if(v == null || v.getValueType() != JsonValue.ValueType.STRING) {
			return null;
		}

		return ((JsonString)v).getString();
	}

	public static Optional<NimrodURI> getAssignmentStateUri(ResultSet rs) throws SQLException {
		NimrodURI nuri = DBUtils.getPrefixedNimrodUri(rs, "tx_");
		if(nuri == null) {
			return Optional.empty();
		}

		String workDir = rs.getString("work_dir");
		if(workDir != null) {
			URI uri;
			try {
				uri = new URI(
						nuri.uri.getScheme(),
						nuri.uri.getUserInfo(),
						nuri.uri.getHost(),
						nuri.uri.getPort(),
						nuri.uri.getPath() + workDir,
						nuri.uri.getQuery(),
						null
				);
			} catch(URISyntaxException e) {
				throw new RuntimeException(e);
			}

			return Optional.of(NimrodURI.create(uri, nuri.certPath, nuri.noVerifyPeer, nuri.noVerifyHost));
		} else {
			return Optional.of(nuri);
		}
	}
}
