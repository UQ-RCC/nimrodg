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
package au.edu.uq.rcc.nimrodg.impl.sqlite3;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBBaseHelper;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgent;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResource;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResourceType;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.json.JsonObject;
import javax.json.JsonStructure;

public class DBResourceHelpers extends DBBaseHelper {

	private final PreparedStatement qGetResourceTypeInfo;
	private final PreparedStatement qGetResourceTypeInfo2;

	private final PreparedStatement qGetResourceById;
	private final PreparedStatement qGetResourceByName;
	private final PreparedStatement qAddResource;
	private final PreparedStatement qDeleteResource;
	private final PreparedStatement qGetResources;

	private final PreparedStatement qGetAssignedResources;
	private final PreparedStatement qAssignResource;
	private final PreparedStatement qUnassignResource;
	private final PreparedStatement qGetAssignmentStatus;

	private final PreparedStatement qIsResourceCapable;
	private final PreparedStatement qAddResourceCaps;
	private final PreparedStatement qRemoveResourceCaps;

	private final PreparedStatement qGetAgentById;
	private final PreparedStatement qGetAgentInformation;
	private final PreparedStatement qGetAgentResource;
	private final PreparedStatement qGetAgentsOnResource;
	private final PreparedStatement qAddAgent;
	private final PreparedStatement qUpdateAgent;

	private final PreparedStatement qAddResourceType;
	private final PreparedStatement qDeleteResourceType;
	private final PreparedStatement qAddAgentPlatform;
	private final PreparedStatement qDelAgentPlatform;
	private final PreparedStatement qMapAgent;
	private final PreparedStatement qUnmapAgent;

	public DBResourceHelpers(Connection conn, List<PreparedStatement> statements) throws SQLException {
		super(conn, statements);
		this.qGetResourceTypeInfo = prepareStatement("SELECT * FROM nimrod_resource_types WHERE name = ?");
		this.qGetResourceTypeInfo2 = prepareStatement("SELECT * FROM nimrod_resource_types");

		this.qGetResourceById = prepareStatement("SELECT * FROM nimrod_full_resources WHERE id = ?");
		this.qGetResourceByName = prepareStatement("SELECT * FROM nimrod_full_resources WHERE name = ?");
		this.qAddResource = prepareStatement("INSERT INTO nimrod_resources(\n"
				+ "	name, type_id, config,\n"
				+ "	amqp_uri, amqp_cert_path, amqp_no_verify_peer, amqp_no_verify_host,\n"
				+ "	tx_uri, tx_cert_path, tx_no_verify_peer, tx_no_verify_host\n"
				+ ")\n"
				+ "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);", true);
		this.qDeleteResource = prepareStatement("DELETE FROM nimrod_resources WHERE id = ?");
		this.qGetResources = prepareStatement("SELECT * FROM nimrod_full_resources");

		this.qGetAssignedResources = prepareStatement("SELECT * FROM nimrod_assigned_resources WHERE exp_id = ?");
		this.qAssignResource = prepareStatement("INSERT OR IGNORE INTO nimrod_resource_assignments(resource_id, exp_id, tx_uri, tx_cert_path, tx_no_verify_peer, tx_no_verify_host) VALUES(?, ?, ?, ?, ?, ?)");
		this.qUnassignResource = prepareStatement("DELETE FROM nimrod_resource_assignments WHERE resource_id = ? AND exp_id = ?");
		this.qGetAssignmentStatus = prepareStatement("SELECT * FROM nimrod_full_resource_assignments WHERE resource_id = ? AND exp_id = ?");

		this.qIsResourceCapable = prepareStatement("SELECT COUNT(id) > 0 AS value FROM nimrod_resource_capabilities WHERE resource_id = ? AND exp_id = ?");
		this.qAddResourceCaps = prepareStatement("INSERT OR IGNORE INTO nimrod_resource_capabilities(resource_id, exp_id) VALUES(?, ?)");
		this.qRemoveResourceCaps = prepareStatement("DELETE FROM nimrod_resource_capabilities WHERE resource_id = ? AND exp_id");

		this.qGetAgentById = prepareStatement("SELECT * FROM nimrod_resource_agents WHERE id = ?");
		this.qGetAgentInformation = prepareStatement("SELECT * FROM nimrod_resource_agents WHERE agent_uuid = ?");
		this.qGetAgentResource = prepareStatement("SELECT * FROM nimrod_full_resources WHERE id = (SELECT location FROM nimrod_resource_agents WHERE agent_uuid = ?)");
		this.qGetAgentsOnResource = prepareStatement("SELECT * FROM nimrod_resource_agents WHERE location = ? AND expired = FALSE");
		this.qAddAgent = prepareStatement("INSERT INTO nimrod_resource_agents(\n"
				+ "	state, queue, agent_uuid, shutdown_signal, shutdown_reason,\n"
				+ "	expiry_time, secret_key, location, actuator_data\n"
				+ ") VALUES(?, ?, ?, ?, ?, ?, COALESCE(?, LOWER(HEX(RANDOMBLOB(16)))), ?, ?)", true);
		this.qUpdateAgent = prepareStatement("UPDATE nimrod_resource_agents SET state = ?, queue = ?, shutdown_signal = ?, shutdown_reason = ?, connected_at = ?, last_heard_from = ?, expiry_time = ?, expired = ?, actuator_data = ? WHERE agent_uuid = ?");

		this.qAddResourceType = prepareStatement(
				"INSERT INTO nimrod_resource_types(name, implementation_class) VALUES (?, ?) "
				+ "ON CONFLICT(name) DO UPDATE SET implementation_class = EXCLUDED.implementation_class",
				true
		);
		this.qDeleteResourceType = prepareStatement("DELETE FROM nimrod_resource_types WHERE name = ?");
		this.qAddAgentPlatform = prepareStatement(
				"INSERT INTO nimrod_agents(platform_string, path) VALUES(?, ?) "
				+ "ON CONFLICT(platform_string) DO UPDATE SET path = EXCLUDED.path"
		);
		this.qDelAgentPlatform = prepareStatement("DELETE FROM nimrod_agents WHERE platform_string = ?");
		this.qMapAgent = prepareStatement(
				"INSERT INTO nimrod_agent_mappings(system, machine, agent_id)"
				+ "SELECT ?, ?, id FROM nimrod_agents WHERE platform_string = ? "
				+ "ON CONFLICT (system, machine) DO UPDATE SET system = EXCLUDED.system, machine = EXCLUDED.machine"
		);
		this.qUnmapAgent = prepareStatement("DELETE FROM nimrod_agents WHERE platform_string = ?");
	}

	public Optional<TempResourceType> getResourceTypeInfo(String name) throws SQLException {
		qGetResourceTypeInfo.setString(1, name);

		try(ResultSet rs = qGetResourceTypeInfo.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}
			return Optional.of(typeFromResultSet(rs));
		}
	}

	public List<TempResourceType> getResourceTypeInfo() throws SQLException {
		List<TempResourceType> tt = new ArrayList<>();

		try(ResultSet rs = qGetResourceTypeInfo2.executeQuery()) {
			while(rs.next()) {
				tt.add(typeFromResultSet(rs));
			}
		}

		return tt;
	}

	public TempResourceType addResourceTypeInfo(String name, String clazz) throws SQLException {
		qAddResourceType.setString(1, name);
		qAddResourceType.setString(2, clazz);
		if(qAddResourceType.executeUpdate() == 0) {
			throw new SQLException("Creating resource type failed, no rows affected");
		}

		long id;
		try(ResultSet rs = qAddResourceType.getGeneratedKeys()) {
			if(rs.next()) {
				id = rs.getLong(1);
			} else {
				throw new SQLException("Creating resource type failed, no id obtained");
			}
		}

		return new TempResourceType(id, name, clazz);
	}

	public boolean deleteResourceTypeInfo(String name) throws SQLException {
		qDeleteResourceType.setString(1, name);
		return qDeleteResourceType.executeUpdate() == 1;
	}

	public Optional<TempResource> getResource(String path) throws SQLException {
		qGetResourceByName.setString(1, path);

		try(ResultSet rs = qGetResourceByName.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(resFromResultSet(rs));
		}
	}

	public Optional<TempResource> getResource(long id) throws SQLException {
		qGetResourceById.setLong(1, id);

		try(ResultSet rs = qGetResourceById.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(resFromResultSet(rs));
		}
	}

	public TempResource addResource(String name, String type, JsonStructure config, NimrodURI amqpUri, NimrodURI txUri) throws SQLException {

		Optional<TempResourceType> _type = getResourceTypeInfo(type);
		if(_type.isEmpty()) {
			throw new NimrodException("No such type '%s'", type);
		}

		qAddResource.setString(1, name);
		qAddResource.setLong(2, _type.get().id);
		qAddResource.setString(3, config.toString());
		DBUtils.setNimrodUri(qAddResource, 4, amqpUri);
		DBUtils.setNimrodUri(qAddResource, 8, txUri);

		if(qAddResource.executeUpdate() == 0) {
			throw new SQLException("Creating resource failed, no rows affected");
		}

		long id;
		try(ResultSet rs = qAddResource.getGeneratedKeys()) {
			if(rs.next()) {
				id = rs.getLong(1);
			} else {
				throw new SQLException("Creating resource failed, no id obtained");
			}
		}

		return getResource(id).get();
	}

	public boolean deleteResource(long id) throws SQLException {
		qDeleteResource.setLong(1, id);
		return qDeleteResource.executeUpdate() != 0;
	}

	public List<TempResource> getResources() throws SQLException {
		try(ResultSet rs = qGetResources.executeQuery()) {
			return resListFromResultSet(rs);
		}
	}

	public List<TempResource> getAssignedResources(long expId) throws SQLException {
		qGetAssignedResources.setLong(1, expId);
		try(ResultSet rs = qGetAssignedResources.executeQuery()) {
			return resListFromResultSet(rs);
		} finally {
			qGetAssignedResources.clearParameters();
		}
	}

	public boolean assignResource(long resId, long expId, NimrodURI txUri) throws SQLException {
		qAssignResource.setLong(1, resId);
		qAssignResource.setLong(2, expId);
		DBUtils.setNimrodUri(qAssignResource, 3, txUri);
		return qAssignResource.executeUpdate() != 0;
	}

	public boolean unassignResource(long resId, long expId) throws SQLException {
		qUnassignResource.setLong(1, resId);
		qUnassignResource.setLong(2, expId);
		return qUnassignResource.executeUpdate() != 0;
	}

	public Optional<NimrodURI> getAssignmentStatus(long resId, long expId) throws SQLException {
		qGetAssignmentStatus.setLong(1, resId);
		qGetAssignmentStatus.setLong(2, expId);

		try(ResultSet rs = qGetAssignmentStatus.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.ofNullable(DBUtils.getPrefixedNimrodUri(rs, "tx_"));
		}
	}

	public boolean isResourceCapable(long resId, long expId) throws SQLException {
		qIsResourceCapable.setLong(1, resId);
		qIsResourceCapable.setLong(2, expId);

		try(ResultSet rs = qIsResourceCapable.executeQuery()) {
			if(!rs.next()) {
				return false;
			}

			return rs.getBoolean("value");
		}
	}

	public boolean addResourceCaps(long resId, long expId) throws SQLException {
		qAddResourceCaps.setLong(1, resId);
		qAddResourceCaps.setLong(2, expId);
		return qAddResourceCaps.executeUpdate() != 0;
	}

	public boolean removeResourceCaps(long resId, long expId) throws SQLException {
		qRemoveResourceCaps.setLong(1, resId);
		qRemoveResourceCaps.setLong(2, expId);
		return qRemoveResourceCaps.executeUpdate() != 0;
	}

	public Optional<TempAgent> getAgentInformationByUUID(UUID uuid) throws SQLException {
		qGetAgentInformation.setString(1, uuid.toString());

		try(ResultSet rs = qGetAgentInformation.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(agentFromResultSet(rs));
		}
	}

	public Optional<TempResource> getAgentResource(UUID uuid) throws SQLException {
		qGetAgentResource.setString(1, uuid.toString());
		try(ResultSet rs = qGetAgentResource.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(resFromResultSet(rs));
		}
	}

	public List<TempAgent> getResourceAgentInformation(long resId) throws SQLException {
		qGetAgentsOnResource.setLong(1, resId);

		try(ResultSet rs = qGetAgentsOnResource.executeQuery()) {
			return agentListFromResultSet(rs);
		}
	}

	public TempAgent addAgent(long resId, AgentState agent) throws SQLException {
		qAddAgent.setString(1, Agent.stateToString(agent.getState()));
		qAddAgent.setString(2, agent.getQueue());
		qAddAgent.setString(3, agent.getUUID().toString());
		qAddAgent.setInt(4, agent.getShutdownSignal());
		qAddAgent.setString(5, AgentShutdown.reasonToString(agent.getShutdownReason()));
		DBUtils.setLongInstant(qAddAgent, 6, agent.getExpiryTime());
		qAddAgent.setString(7, agent.getSecretKey());
		qAddAgent.setLong(8, resId);
		JsonObject data = agent.getActuatorData();
		if(data == null) {
			qAddAgent.setString(9, null);
		} else {
			qAddAgent.setString(9, data.toString());
		}

		if(qAddAgent.executeUpdate() == 0) {
			throw new SQLException("Creating agent failed, no rows affected");
		}

		long id;
		try(ResultSet rs = qAddAgent.getGeneratedKeys()) {
			if(rs.next()) {
				id = rs.getLong(1);
			} else {
				throw new SQLException("Creating agent failed, no id obtained");
			}
		}

		qGetAgentById.setLong(1, id);
		try(ResultSet rs = qGetAgentById.executeQuery()) {
			if(!rs.next()) {
				throw new SQLException("Creating agent failed, unable to retrieve info");
			}

			return agentFromResultSet(rs);
		}
	}

	public boolean updateAgent(AgentState agent) throws SQLException {
		qUpdateAgent.setString(1, Agent.stateToString(agent.getState()));
		qUpdateAgent.setString(2, agent.getQueue());
		qUpdateAgent.setInt(3, agent.getShutdownSignal());
		qUpdateAgent.setString(4, AgentShutdown.reasonToString(agent.getShutdownReason()));
		DBUtils.setLongInstant(qUpdateAgent, 5, agent.getConnectionTime());
		DBUtils.setLongInstant(qUpdateAgent, 6, agent.getLastHeardFrom());
		DBUtils.setLongInstant(qUpdateAgent, 7, agent.getExpiryTime());
		qUpdateAgent.setBoolean(8, agent.getExpired());

		JsonObject data = agent.getActuatorData();
		if(data == null) {
			qUpdateAgent.setString(9, null);
		} else {
			qUpdateAgent.setString(9, data.toString());
		}

		qUpdateAgent.setString(10, agent.getUUID().toString());

		return qUpdateAgent.executeUpdate() != 0;
	}


	public boolean addAgentPlatform(String platformString, Path path) throws SQLException {
		qAddAgentPlatform.setString(1, platformString);
		qAddAgentPlatform.setString(2, path.toString());
		return qAddAgentPlatform.executeUpdate() == 1;
	}

	public boolean deleteAgentPlatform(String platformString) throws SQLException {
		qDelAgentPlatform.setString(1, platformString);
		return qDelAgentPlatform.executeUpdate() == 1;
	}

	public boolean mapAgentPosixPlatform(String platformString, String system, String machine) throws SQLException {
		qMapAgent.setString(1, system);
		qMapAgent.setString(2, machine);
		qMapAgent.setString(3, platformString);
		return qMapAgent.executeUpdate() == 1;
	}

	public boolean unmapAgentPosixPlatform(String system, String machine) throws SQLException {
		qUnmapAgent.setString(1, system);
		qUnmapAgent.setString(2, machine);
		return qUnmapAgent.executeUpdate() == 1;
	}

	private static TempResourceType typeFromResultSet(ResultSet rs) throws SQLException {
		return new TempResourceType(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("implementation_class")
		);
	}

	private static List<TempResource> resListFromResultSet(ResultSet rs) throws SQLException {
		List<TempResource> ress = new ArrayList<>();
		while(rs.next()) {
			ress.add(resFromResultSet(rs));
		}
		return ress;
	}

	private static TempResource resFromResultSet(ResultSet rs) throws SQLException {
		return new TempResource(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("type_name"),
				rs.getString("type_class"),
				DBUtils.getJSONObject(rs, "config"),
				DBUtils.getPrefixedNimrodUri(rs, "amqp_"),
				DBUtils.getPrefixedNimrodUri(rs, "tx_")
		);
	}

	private List<TempAgent> agentListFromResultSet(ResultSet rs) throws SQLException {
		List<TempAgent> taa = new ArrayList<>();
		while(rs.next()) {
			taa.add(agentFromResultSet(rs));
		}
		return taa;
	}

	private static TempAgent agentFromResultSet(ResultSet rs) throws SQLException {
		return new TempAgent(
				rs.getLong("id"),
				Agent.stateFromString(rs.getString("state")),
				rs.getString("queue"),
				UUID.fromString(rs.getString("agent_uuid")),
				rs.getInt("shutdown_signal"),
				AgentShutdown.reasonFromString(rs.getString("shutdown_reason")),
				DBUtils.getLongInstant(rs, "created"),
				DBUtils.getLongInstant(rs, "connected_at"),
				DBUtils.getLongInstant(rs, "last_heard_from"),
				DBUtils.getLongInstant(rs, "expiry_time"),
				rs.getBoolean("expired"),
				rs.getString("secret_key"),
				rs.getLong("location"),
				DBUtils.getJSONObject(rs, "actuator_data")
		);
	}
}
