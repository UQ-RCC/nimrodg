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
package au.edu.uq.rcc.nimrodg.impl.sqlite3;

import au.edu.uq.rcc.nimrodg.api.MachinePair;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.SQLUUUUU;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempConfig;
import au.edu.uq.rcc.nimrodg.api.setup.AMQPConfig;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI.SetupException;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.api.setup.TransferConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * SQLite3 Setup API for Nimrod.
 *
 * Queries can't be prepared here as the tables may not exist.
 */
public class SQLite3SetupAPI extends SQLUUUUU<SetupException> implements NimrodSetupAPI {

	private static final String[] DATABASE_FILES = new String[]{
		"db/setup/00-config.sql",
		"db/setup/01-experiments.sql",
		"db/setup/02-resources.sql",
		"db/setup/03-messages.sql"
	};

	private final Connection conn;
	private final boolean managed;

	SQLite3SetupAPI(Connection conn) {
		this(conn, true);
	}

	SQLite3SetupAPI(Connection conn, boolean managed) {
		this.conn = conn;
		this.managed = managed;
	}

	@Override
	public synchronized boolean isCompatibleSchema() throws SetupException {
		try {
			return SQLite3APIFactory.isSchemaCompatible(conn);
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized void reset() throws SetupException {
		String dbData;
		/* Collate all the schema files together. */
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(String s : DATABASE_FILES) {
				try(InputStream is = SQLite3APIFactory.class.getResourceAsStream(s)) {
					byte[] d = is.readAllBytes();
					baos.write(d, 0, d.length);
					/* In case the last line is a comment */
					baos.write('\n');
				}
			}

			dbData = baos.toString(StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new SetupException(e);
		}

		/* Dump everything to the database. */
		this.runSQLTransaction(() -> {
			try(Statement s = conn.createStatement()) {
				s.executeUpdate(dbData);
			}
		});
	}

	private void _setupT(SetupConfig cfg) throws SQLException {
		boolean configExists = false;
		try(PreparedStatement ps = conn.prepareStatement("SELECT id FROM nimrod_config WHERE id = 1")) {
			try(ResultSet rs = ps.executeQuery()) {
				if(rs.next()) {
					configExists = true;
				}
			}
		}

		AMQPConfig amqp = cfg.amqp();
		TransferConfig tx = cfg.transfer();

		TempConfig newCfg = new TempConfig(
				cfg.workDir(),
				cfg.storeDir(),
				NimrodURI.create(
						amqp.uri(),
						amqp.certPath(),
						amqp.noVerifyPeer(),
						amqp.noVerifyHost()
				),
				amqp.routingKey(),
				NimrodURI.create(
						tx.uri(),
						tx.certPath(),
						tx.noVerifyPeer(),
						tx.noVerifyHost()
				)
		);

		String queryString;
		if(!configExists) {
			queryString = "INSERT INTO nimrod_config VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		} else {
			queryString = "UPDATE nimrod_config SET"
					+ "id = ?,\n"
					+ "work_dir = ?,\n"
					+ "store_dir = ?,\n"
					+ "amqp_uri = ?,\n"
					+ "amqp_cert_path = ?,\n"
					+ "amqp_no_verify_peer = ?,\n"
					+ "amqp_no_verify_host = ?,\n"
					+ "amqp_routing_key = ?,\n"
					+ "tx_uri = ?,\n"
					+ "tx_cert_path = ?,\n"
					+ "tx_no_verify_peer = ?,\n"
					+ "tx_no_verify_host = ?";
		}
		try(PreparedStatement ps = conn.prepareStatement(queryString)) {
			ps.setLong(1, 1);
			ps.setString(2, newCfg.workDir);
			ps.setString(3, newCfg.storeDir);
			DBUtils.setNimrodUri(ps, 4, newCfg.amqpUri);
			ps.setString(8, newCfg.amqpRoutingKey);
			DBUtils.setNimrodUri(ps, 9, newCfg.txUri);
			ps.executeUpdate();
		}

		{
			/* Agents */
			Map<String, Path> agents = cfg.agents();
			try(PreparedStatement ps = prepareAddAgent()) {
				for(Map.Entry<String, Path> a : agents.entrySet()) {
					ps.setString(1, a.getKey());
					ps.setString(2, a.getValue().toString());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		}

		{
			/* Agent mappings */
			Map<MachinePair, String> map = cfg.agentMappings();
			try(PreparedStatement ps = prepareMapAgent()) {
				for(Map.Entry<MachinePair, String> m : map.entrySet()) {
					ps.setString(1, m.getKey().system());
					ps.setString(2, m.getKey().machine());
					ps.setString(3, m.getValue());
					ps.addBatch();
				}

				ps.executeBatch();
			}
		}

		{
			/* Resource Types */
			Map<String, String> res = cfg.resourceTypes();
			try(PreparedStatement ps = prepareAddResourceType()) {
				for(Map.Entry<String, String> r : res.entrySet()) {
					ps.setString(1, r.getKey());
					ps.setString(2, r.getValue());
					ps.addBatch();
				}

				ps.executeBatch();
			}
		}

		{
			/* Properties */
			Map<String, String> props = cfg.properties();
			try(PreparedStatement ps = prepareInsertProperty()) {
				for(Map.Entry<String, String> p : props.entrySet()) {
					ps.setString(1, p.getKey());
					ps.setString(2, p.getValue());
					ps.addBatch();
				}

				ps.executeBatch();
			}
		}
	}

	private PreparedStatement prepareInsertProperty() throws SQLException {
		return conn.prepareStatement("INSERT OR REPLACE INTO nimrod_kv_config(key, value) VALUES(?, ?)");
	}

	private PreparedStatement prepareAddAgent() throws SQLException {
		return conn.prepareStatement("INSERT INTO nimrod_agents(platform_string, path) VALUES(?, ?)");
	}

	private PreparedStatement prepareMapAgent() throws SQLException {
		return conn.prepareStatement(
				"INSERT INTO nimrod_agent_mappings(system, machine, agent_id)"
				+ "SELECT ?, ?, id FROM nimrod_agents WHERE platform_string = ?");
	}

	private PreparedStatement prepareAddResourceType() throws SQLException {
		return conn.prepareStatement("INSERT INTO nimrod_resource_types(name, implementation_class) VALUES (?, ?)");
	}

	private String _getProperty(String prop) throws SQLException {
		try(PreparedStatement ps = conn.prepareStatement("SELECT value FROM nimrod_kv_config WHERE key = ?")) {
			ps.setString(1, prop);
			try(ResultSet rs = ps.executeQuery()) {
				if(!rs.next()) {
					return null;
				}
				return rs.getString("value");
			}
		}
	}

	private String _setPropertyT(String prop, String val) throws SQLException {
		String old = _getProperty(prop);
		if(val == null || val.isEmpty()) {
			try(PreparedStatement ps = conn.prepareStatement("DELETE FROM nimrod_kv_config WHERE key = ?")) {
				ps.setString(1, prop);
				ps.executeUpdate();
			}
		} else {
			try(PreparedStatement ps = prepareInsertProperty()) {
				ps.setString(1, prop);
				ps.setString(2, val);
				ps.executeUpdate();
			}
		}

		return old;
	}

	@Override
	public synchronized void setup(SetupConfig cfg) throws SetupException {
		this.runSQLTransaction(() -> _setupT(cfg));
	}

	@Override
	public synchronized String getProperty(String prop) throws SetupException {
		return this.runSQL(() -> _getProperty(prop));
	}

	@Override
	public synchronized String setProperty(String prop, String val) throws SetupException {
		return this.runSQLTransaction(() -> _setPropertyT(prop, val));
	}

	@Override
	public synchronized boolean addResourceType(String name, String clazz) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = prepareAddResourceType()) {
				ps.setString(1, name);
				ps.setString(2, clazz);
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized boolean deleteResourceType(String name) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = conn.prepareStatement("DELETE FROM nimrod_resource_types WHERE name = ?")) {
				ps.setString(1, name);
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized boolean addAgent(String platformString, Path path) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = prepareAddAgent()) {
				ps.setString(1, platformString);
				ps.setString(2, path.toString());
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized boolean deleteAgent(String platformString) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = conn.prepareStatement("DELETE FROM nimrod_agents WHERE platform_string = ?")) {
				ps.setString(1, platformString);
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized boolean mapAgent(String platformString, String system, String machine) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = prepareMapAgent()) {
				ps.setString(1, system);
				ps.setString(2, machine);
				ps.setString(3, platformString);
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized boolean unmapAgent(String system, String machine) throws SetupException {
		return this.runSQL(() -> {
			try(PreparedStatement ps = conn.prepareStatement("DELETE FROM nimrod_agent_mappings WHERE system = ? AND machine = ?")) {
				ps.setString(1, system);
				ps.setString(2, machine);
				return ps.executeUpdate() == 1;
			}
		});
	}

	@Override
	public synchronized void close() throws SetupException {
		try {
			if(managed) {
				conn.close();
			}
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	protected Connection getConnection() {
		return conn;
	}

	@Override
	protected SetupException makeException(SQLException e) {
		return new SetupException(e);
	}
}
