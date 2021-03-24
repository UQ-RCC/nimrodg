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

import au.edu.uq.rcc.nimrodg.api.MasterResourceType;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
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

	public static NimrodURI mergeNimrodURI(NimrodURI oldUri, NimrodURI newUri) {
		if(oldUri == null) {
			return newUri;
		}

		if(newUri == null) {
			return oldUri;
		}

		return NimrodURI.create(
				NimrodUtils.coalesce(newUri.uri, oldUri.uri),
				NimrodUtils.coalesce(newUri.certPath, oldUri.certPath),
				NimrodUtils.coalesce(newUri.noVerifyPeer, oldUri.noVerifyPeer),
				NimrodUtils.coalesce(newUri.noVerifyHost, oldUri.noVerifyHost)
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
				NimrodUtils.coalesce(newCfg.workDir, oldCfg.workDir),
				NimrodUtils.coalesce(newCfg.storeDir, oldCfg.storeDir),
				mergeNimrodURI(oldCfg.amqpUri, newCfg.amqpUri),
				NimrodUtils.coalesce(newCfg.amqpRoutingKey, oldCfg.amqpRoutingKey),
				mergeNimrodURI(oldCfg.txUri, newCfg.txUri)
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

	public static MasterResourceType createType(String className) throws ReflectiveOperationException {
		return createType(Class.forName(className));
	}

	public static MasterResourceType createType(Class<?> clazz) throws ReflectiveOperationException {

		if(!ResourceType.class.isAssignableFrom(clazz)) {
			/* Specified class doesn't implement ResourceType */
			return null;
		}

		return (MasterResourceType)clazz.getConstructor().newInstance();
	}

	public static ResourceTypeInfo createTypeInfo(TempResourceType trt) {
		Class<?> clazz;

		try {
			clazz = Class.forName(trt.clazz);
		} catch(ClassNotFoundException e) {
			clazz = null;
		}

		if(clazz == null) {
			return new ResourceTypeInfo(trt.name, trt.clazz, clazz, null);
		}

		MasterResourceType rt;

		if(!ResourceType.class.isAssignableFrom(clazz)) {
			rt = null;
		} else try {
			rt = (MasterResourceType)clazz.getConstructor().newInstance();
		} catch(ReflectiveOperationException e) {
			rt = null;
		}

		return new ResourceTypeInfo(trt.name, trt.clazz, clazz, rt);
	}

	public static MigrationPlan buildMigrationPlan(final SchemaVersion from, final SchemaVersion to, final Map<SchemaVersion, UpgradeStep> upgradeSteps) {
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(upgradeSteps, "steps");

		if(from.compareTo(to) > 0) {
			return MigrationPlan.invalid(from, to, "Can't upgrade to an older version");
		}

		if(from.compareTo(SchemaVersion.of(1, 0, 0)) < 0) {
			/*
			 * Due to early screw-ups, this can't safely be done automatically.
			 * Upgrade manually to 1.0.0 first, then we can do things.
			 */
			return MigrationPlan.invalid(from, to, "Please upgrade to 1.0.0 manually, then rerun this command");
		}

		/* Find an upgrade path. */
		final ArrayList<UpgradeStep> steps = new ArrayList<>(upgradeSteps.size());
		for(SchemaVersion _from = from; _from.compareTo(to) < 0; ) {
			UpgradeStep step = upgradeSteps.get(_from);
			if(step == null) {
				return MigrationPlan.invalid(_from, to, "Unable to determine upgrade path");
			}

			assert step.from.equals(_from);
			assert step.from.compareTo(step.to) < 0;

			steps.add(step);
			_from = step.to;
		}

		if(!steps.isEmpty() && !steps.get(steps.size() - 1).to.equals(to)) {
			/* We overshot the target. */
			return MigrationPlan.invalid(from, to, "Unable to determine upgrade path");
		}

		return MigrationPlan.valid(from, to, steps);
	}

	public static String combineEmbeddedFiles(Class<?> clazz, String... fileList) {
		/* Collate all the schema files together. */
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(String s : fileList) {
				baos.write(NimrodUtils.readEmbeddedFile(clazz, s));
				/* In case the last line is a comment */
				baos.write('\n');
			}

			return baos.toString(StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
