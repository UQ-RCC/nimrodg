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
package au.edu.uq.rcc.nimrodg.impl.postgres;

import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SetupAPIImpl implements NimrodSetupAPI {

	private static final String[] DATABASE_FILES = new String[]{
		"db/00-ddl.sql",
		"db/01-ddl-experiments.sql",
		"db/02-ddl-exporttasks.sql",
		"db/03-ddl-add-compiledexperiment.sql",
		"db/04-ddl-utility.sql",
		"db/05-ddl-resources.sql",
		"db/06-ddl-resource-utility.sql",
		"db/07-ddl-messages.sql"
	};

	private final Connection conn;
	private final boolean managed;
	private final PreparedStatement qGetProperty;
	private final PreparedStatement qSetProperty;
	private final PreparedStatement qAddResourceType;
	private final PreparedStatement qDeleteResourceType;
	private final PreparedStatement qAddAgent;
	private final PreparedStatement qDelAgent;
	private final PreparedStatement qMapAgent;
	private final PreparedStatement qUnmapAgent;
	private final PreparedStatement qUpdateConfig;

	public SetupAPIImpl(Connection conn) throws SQLException {
		this(conn, true);
	}

	SetupAPIImpl(Connection conn, boolean manage) throws SQLException {
		this.conn = conn;
		this.managed = manage;

		this.qUpdateConfig = conn.prepareStatement(
				"SELECT update_config(?, ?, make_uri(?, ?, ?, ?), ?, make_uri(?, ?, ?, ?))"
		);

		this.qGetProperty = conn.prepareStatement("SELECT get_property(?) AS value");
		this.qSetProperty = conn.prepareStatement("SELECT set_property(?, ?) AS value");

		this.qAddResourceType = conn.prepareStatement(
				"INSERT INTO nimrod_resource_types(name, implementation_class) VALUES (?, ?)"
		);

		this.qDeleteResourceType = conn.prepareStatement("DELETE FROM nimrod_resource_types WHERE name = ?");

		this.qAddAgent = conn.prepareStatement("INSERT INTO nimrod_agents(platform_string, path) VALUES(?, ?)");
		this.qDelAgent = conn.prepareStatement("DELETE FROM nimrod_agents WHERE platform_string = ?");

		this.qMapAgent = conn.prepareStatement(
				"INSERT INTO nimrod_agent_mappings(system, machine, agent_id)"
				+ "SELECT ?, ?, id FROM nimrod_agents WHERE platform_string = ?"
		);

		this.qUnmapAgent = conn.prepareStatement(
				"DELETE FROM nimrod_agent_mappings WHERE system = ? AND machine = ?"
		);
	}

	@Override
	public synchronized void reset() throws SetupException {
		String dbData;
		/* Collate all the schema files together. */
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			for(String s : DATABASE_FILES) {
				try(InputStream is = NimrodAPIFactoryImpl.class.getResourceAsStream(s)) {
					byte[] d = is.readAllBytes();
					baos.write(d, 0, d.length);
					/* In case the last line is a comment */
					baos.write('\n');
				}
			}

			dbData = baos.toString("UTF-8");
		} catch(IOException e) {
			throw new SetupException(e);
		}

		/* Dump everything to the database. */
		try(Statement s = conn.createStatement()) {
			s.execute(dbData);
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized void setup(SetupConfig cfg) throws SetupException {
		try {
			qUpdateConfig.setString(1, cfg.workDir());
			qUpdateConfig.setString(2, cfg.storeDir());
			qUpdateConfig.setString(3, cfg.amqp().uri().toString());
			qUpdateConfig.setString(4, cfg.amqp().certPath());
			qUpdateConfig.setBoolean(5, cfg.amqp().noVerifyPeer());
			qUpdateConfig.setBoolean(6, cfg.amqp().noVerifyHost());
			qUpdateConfig.setString(7, cfg.amqp().routingKey());
			qUpdateConfig.setString(8, cfg.transfer().uri().toString());
			qUpdateConfig.setString(9, cfg.transfer().certPath());
			qUpdateConfig.setBoolean(10, cfg.transfer().noVerifyPeer());
			qUpdateConfig.setBoolean(11, cfg.transfer().noVerifyHost());

			try(ResultSet rs = qUpdateConfig.executeQuery()) {
				if(!rs.next()) {
					throw new SetupException("update_config() returned no rows");
				}
			}

			cfg.agents().entrySet().forEach(e -> addAgent(e.getKey(), e.getValue()));
			cfg.agentMappings().entrySet().forEach(e -> mapAgent(e.getValue(), e.getKey().system(), e.getKey().machine()));
			cfg.resourceTypes().entrySet().forEach(e -> addResourceType(e.getKey(), e.getValue()));
			cfg.properties().entrySet().forEach(e -> setProperty(e.getKey(), e.getValue()));

		} catch(SQLException e) {
			throw new SetupException(e);
		}

	}

	@Override
	public synchronized String getProperty(String prop) throws SetupException {
		try {
			qGetProperty.setString(1, prop);
			try(ResultSet rs = qGetProperty.executeQuery()) {
				if(!rs.next()) {
					throw new SetupException("get_property() returned no rows");
				}

				return rs.getString("value");
			}
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized String setProperty(String prop, String val) throws SetupException {
		try {
			qSetProperty.setString(1, prop);
			qSetProperty.setString(2, val);
			try(ResultSet rs = qSetProperty.executeQuery()) {
				if(!rs.next()) {
					throw new SetupException("get_property() returned no rows");
				}

				return rs.getString("value");
			}
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean addResourceType(String name, Class<?> clazz) throws SetupException {
		if(name == null || clazz == null) {
			throw new IllegalArgumentException();
		}

		return addResourceType(name, clazz.getCanonicalName());
	}

	@Override
	public synchronized boolean addResourceType(String name, String clazz) throws SetupException {
		if(name == null || clazz == null) {
			throw new IllegalArgumentException();
		}

		try {
			qAddResourceType.setString(1, name);
			qAddResourceType.setString(2, clazz);
			return qAddResourceType.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean deleteResourceType(String name) throws SetupException {
		if(name == null) {
			throw new IllegalArgumentException();
		}

		try {
			qDeleteResourceType.setString(1, name);
			return qDeleteResourceType.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean addAgent(String platformString, String path) throws SetupException {
		if(platformString == null || path == null) {
			throw new IllegalArgumentException();
		}

		try {
			qAddAgent.setString(1, platformString);
			qAddAgent.setString(2, path);
			return qAddAgent.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean deleteAgent(String platformString) throws SetupException {
		if(platformString == null) {
			throw new IllegalArgumentException();
		}

		try {
			qDelAgent.setString(1, platformString);
			return qDelAgent.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean mapAgent(String platformString, String system, String machine) throws SetupException {
		if(platformString == null || system == null || machine == null) {
			throw new IllegalArgumentException();
		}

		try {
			qMapAgent.setString(1, system);
			qMapAgent.setString(2, machine);
			qMapAgent.setString(3, platformString);
			return qMapAgent.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
	}

	@Override
	public synchronized boolean unmapAgent(String system, String machine) throws SetupException {
		if(system == null || machine == null) {
			throw new IllegalArgumentException();
		}

		try {
			qUnmapAgent.setString(1, system);
			qUnmapAgent.setString(2, machine);
			return qUnmapAgent.executeUpdate() == 1;
		} catch(SQLException e) {
			throw new SetupException(e);
		}
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
}
