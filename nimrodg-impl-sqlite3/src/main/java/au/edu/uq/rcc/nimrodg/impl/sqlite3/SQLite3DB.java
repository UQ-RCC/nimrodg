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

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.MachinePair;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.events.ConfigChangeMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.impl.base.db.BrokenDBInvariantException;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodDBAPI;
import au.edu.uq.rcc.nimrodg.impl.base.db.SQLUUUUU;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgent;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgentDefinition;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempCommandResult;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempConfig;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempExperiment;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJob;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJobAttempt;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResource;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResourceType;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonValue;

public class SQLite3DB extends SQLUUUUU<NimrodException.DbError> implements NimrodDBAPI, AutoCloseable {

	private final Connection conn;

	private final ArrayList<PreparedStatement> statements;

	private final PreparedStatement qSelectConfig;
	private final PreparedStatement qInsertConfig;
	private final PreparedStatement qUpdateConfig;
	private final PreparedStatement qGetProperty;
	private final PreparedStatement qUpdateProperty;
	private final PreparedStatement qInsertProperty;
	private final PreparedStatement qDeleteProperty;
	private final PreparedStatement qGetProperties;
	private final PreparedStatement qGetAgentDefinition;
	private final PreparedStatement qGetAgentDefinitionByPlatform;
	private final PreparedStatement qGetAgentDefinitionByPosix;

	private final DBExperimentHelpers experimentHelpers;
	private final DBResourceHelpers resourceHelpers;

	private final PreparedStatement qGetMasterMessages;
	private final PreparedStatement qDeleteMasterMessages;
	private final PreparedStatement qAddMasterMessage;

	public SQLite3DB(Connection conn) throws SQLException {
		this.conn = conn;
		this.statements = new ArrayList<>();
		this.qSelectConfig = prepareStatement("SELECT * FROM nimrod_config WHERE id = 1");
		this.qInsertConfig = prepareStatement("INSERT INTO nimrod_config VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
		this.qUpdateConfig = prepareStatement("UPDATE nimrod_config SET\n"
				+ "id = ?,\n"
				+ "work_dir = ?,\n"
				+ "store_dir= ?,\n"
				+ "amqp_uri = ?,\n"
				+ "amqp_cert_path = ?,\n"
				+ "amqp_no_verify_peer = ?,\n"
				+ "amqp_no_verify_host = ?,\n"
				+ "amqp_routing_key = ?,\n"
				+ "tx_uri = ?,\n"
				+ "tx_cert_path = ?,\n"
				+ "tx_no_verify_peer = ?,\n"
				+ "tx_no_verify_host = ?");

		{
			this.qGetProperty = prepareStatement("SELECT value FROM nimrod_kv_config WHERE key = ?");

			/* NB: Argument order is the same. */
			this.qUpdateProperty = prepareStatement("UPDATE nimrod_kv_config SET value = ? WHERE key = ?");
			this.qInsertProperty = prepareStatement("INSERT INTO nimrod_kv_config(value, key) VALUES(?, ?)");

			this.qDeleteProperty = prepareStatement("DELETE FROM nimrod_kv_config WHERE key = ?");
			this.qGetProperties = prepareStatement("SELECT key, value FROM nimrod_kv_config");
		}

		this.qGetAgentDefinition = prepareStatement("SELECT * FROM nimrod_agentinfo_by_platform");
		this.qGetAgentDefinitionByPlatform = prepareStatement("SELECT * FROM nimrod_agentinfo_by_platform WHERE platform_string = ?");
		this.qGetAgentDefinitionByPosix = prepareStatement("SELECT * FROM nimrod_agentinfo_by_posix WHERE system = ? AND machine = ?");

		this.experimentHelpers = new DBExperimentHelpers(conn, statements);
		this.resourceHelpers = new DBResourceHelpers(conn, statements);

		this.qGetMasterMessages = prepareStatement("SELECT * FROM nimrod_master_message_storage ORDER BY ts ASC");
		this.qDeleteMasterMessages = prepareStatement("DELETE FROM nimrod_master_message_storage");
		this.qAddMasterMessage = prepareStatement("INSERT INTO nimrod_master_message_storage(operation, class, payload) VALUES(?, ?, ?)");
	}

	private PreparedStatement prepareStatement(String s) throws SQLException {
		PreparedStatement ps = conn.prepareStatement(s);
		statements.add(ps);
		return ps;
	}

	@Override
	protected Connection getConnection() {
		return conn;
	}

	@Override
	public synchronized NimrodConfig getConfig() throws SQLException {
		try(ResultSet rs = qSelectConfig.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("getConfig() received no rows");
			}

			return DBUtils.configFromResultSet(rs).create();
		}
	}

	@Override
	public synchronized NimrodConfig updateConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri) throws SQLException {
		TempConfig oldCfg = null;
		try(ResultSet rs = qSelectConfig.executeQuery()) {
			if(rs.next()) {
				oldCfg = DBUtils.configFromResultSet(rs);
			}
		}

		TempConfig setupCfg = new TempConfig(workDir, storeDir, amqpUri, amqpRoutingKey, txUri);

		TempConfig newCfg = DBUtils.mergeConfig(oldCfg, setupCfg);

		PreparedStatement ps;
		if(oldCfg == null) {
			ps = qInsertConfig;
		} else {
			ps = qUpdateConfig;
		}

		ps.setLong(1, 1);
		ps.setString(2, newCfg.workDir);
		ps.setString(3, newCfg.storeDir);
		DBUtils.setNimrodUri(ps, 4, newCfg.amqpUri);
		ps.setString(8, newCfg.amqpRoutingKey);
		DBUtils.setNimrodUri(ps, 9, newCfg.txUri);
		ps.executeUpdate();

		return newCfg.create();
	}

	@Override
	public synchronized String getProperty(String key) throws SQLException {
		qGetProperty.setString(1, key);
		try(ResultSet rs = qGetProperty.executeQuery()) {
			if(rs.next()) {
				return rs.getString("value");
			} else {
				return null;
			}
		}
	}

	@Override
	public synchronized String setProperty(String key, String value) throws SQLException {
		String old = getProperty(key);
		if(value == null) {
			qDeleteProperty.setString(1, key);
			qDeleteProperty.executeUpdate();
			addConfigChangeMessage(key, old, value);
			return old;
		}

		PreparedStatement ps;
		if(old == null) {
			ps = qInsertProperty;
		} else {
			ps = qUpdateProperty;
		}
		ps.setString(1, value);
		ps.setString(2, key);
		ps.executeUpdate();

		addConfigChangeMessage(key, old, value);
		return old;
	}

	@Override
	public synchronized Map<String, String> getProperties() throws SQLException {
		Map<String, String> props = new HashMap<>();
		try(ResultSet rs = qGetProperties.executeQuery()) {
			while(rs.next()) {
				props.put(rs.getString("key"), rs.getString("value"));
			}
		}

		return props;
	}

	private Map<String, TempAgentDefinition.Impl> agentMappingsFromResultSet(ResultSet rs) throws SQLException {
		Map<String, TempAgentDefinition.Impl> mappings = new HashMap<>();
		while(rs.next()) {
			long id = rs.getLong("id");
			String plat = rs.getString("platform_string");
			String path = rs.getString("path");

			TempAgentDefinition.Impl tad = NimrodUtils.getOrAddLazy(mappings, plat,
					p -> new TempAgentDefinition(id, plat, path, new HashSet<>()).create()
			);

			/* These may be null if there's no mappings. */
			String system = rs.getString("system");
			String machine = rs.getString("machine");
			if(system != null && machine != null) {
				tad.base.posixMappings.add(MachinePair.of(system, machine));
			}
		}

		return mappings;
	}

	@Override
	public synchronized Map<String, TempAgentDefinition.Impl> lookupAgents() throws SQLException {
		try(ResultSet rs = qGetAgentDefinition.executeQuery()) {
			return agentMappingsFromResultSet(rs);
		}
	}

	@Override
	public synchronized Optional<TempAgentDefinition.Impl> lookupAgentByPlatform(String platform) throws SQLException {
		qGetAgentDefinitionByPlatform.setString(1, platform);
		try(ResultSet rs = qGetAgentDefinitionByPlatform.executeQuery()) {
			return Optional.ofNullable(agentMappingsFromResultSet(rs).get(platform));
		}
	}

	@Override
	public synchronized Optional<TempAgentDefinition.Impl> lookupAgentByPOSIX(String system, String machine) throws SQLException {
		qGetAgentDefinitionByPosix.setString(1, system);
		qGetAgentDefinitionByPosix.setString(2, machine);
		try(ResultSet rs = qGetAgentDefinitionByPosix.executeQuery()) {
			return agentMappingsFromResultSet(rs).values().stream().findAny();
		}
	}

	@Override
	public synchronized TempExperiment.Impl addExperiment(String name, String workDir, CompiledRun r) throws SQLException {
		return experimentHelpers.addCompiledExperiment(name, workDir, r).create(this);
	}

	@Override
	public synchronized List<TempExperiment.Impl> listExperiments() throws SQLException {
		return experimentHelpers.listExperiments().stream().map(e -> e.create(this)).collect(Collectors.toList());
	}

	@Override
	public synchronized Optional<TempExperiment.Impl> getExperiment(String name) throws SQLException {
		return experimentHelpers.getExperiment(name).map(e -> e.create(this));
	}

	@Override
	public synchronized Optional<TempExperiment.Impl> getExperiment(long id) throws SQLException {
		return experimentHelpers.getExperiment(id).map(e -> e.create(this));
	}

	@Override
	public synchronized boolean deleteExperiment(long id) throws SQLException {
		return experimentHelpers.deleteExperiment(id);
	}

	@Override
	public synchronized boolean deleteExperiment(String name) throws SQLException {
		return experimentHelpers.deleteExperiment(name);
	}

	@Override
	public synchronized Optional<TempExperiment> getTempExp(long id) throws SQLException {
		return experimentHelpers.getExperiment(id);
	}

	@Override
	public synchronized void updateExperimentState(TempExperiment.Impl exp, Experiment.State state) throws SQLException {
		experimentHelpers.updateExperimentState(exp.base.id, state);
	}

	@Override
	public synchronized Optional<TempJob.Impl> getSingleJobT(long jobId) throws SQLException {
		Optional<TempJob> _job = experimentHelpers.getSingleJob(jobId);
		if(_job.isEmpty()) {
			return Optional.empty();
		}

		TempJob job = _job.get();

		Optional<Experiment> exp = this.getTempExp(job.expId).map(e -> e.create(this));
		if(exp.isEmpty()) {
			/* Shouldn't be possible, is this running in a transaction? */
			throw new IllegalStateException();
		}

		return Optional.of(job.create(exp.get()));
	}

	@Override
	public synchronized JobAttempt.Status getJobStatus(TempJob.Impl job) throws SQLException {
		return experimentHelpers.getJobStatus(job.base.id);
	}

	@Override
	public synchronized List<TempJob.Impl> filterJobs(TempExperiment.Impl exp, EnumSet<JobAttempt.Status> status, long start, long limit) throws SQLException {
		return experimentHelpers.filterJobs(exp.base.id, status, start, limit).stream().map(tj -> tj.create(exp)).collect(Collectors.toList());
	}

	@Override
	public synchronized List<TempJob.Impl> addJobs(TempExperiment.Impl exp, Collection<Map<String, String>> vars) throws SQLException {
		List<TempJob.Impl> jobs = experimentHelpers.addJobs(exp.base.id, vars).stream().map(tj -> tj.create(exp)).collect(Collectors.toList());

		if(exp.getState() != Experiment.State.STOPPED) {
			for(TempJob.Impl j : jobs) {
				addJobMessage(j.base.id, exp.base.id, j.base.jobIndex, j.base.created);
			}
		}
		return jobs;
	}

	@Override
	public synchronized TempJobAttempt.Impl createJobAttempt(TempJob.Impl job, UUID uuid) throws SQLException {
		return experimentHelpers.createJobAttempt(job.base.id, uuid).create(this, job);
	}

	@Override
	public synchronized void startJobAttempt(TempJobAttempt.Impl att, UUID agentUuid) throws SQLException {
		experimentHelpers.startJobAttempt(att.base.id, agentUuid);
	}

	@Override
	public synchronized void finishJobAttempt(TempJobAttempt.Impl att, boolean failed) throws SQLException {
		experimentHelpers.finishJobAttempt(att.base.id, failed);
	}

	@Override
	public List<TempJobAttempt.Impl> filterJobAttempts(Map<Long, TempJob.Impl> jobs, EnumSet<JobAttempt.Status> status) throws SQLException {
		List<TempJobAttempt> atts = new ArrayList<>();
		/* NB: Can't flatMap() due to SQLException */
		for(TempJob.Impl tj : jobs.values()) {
			atts.addAll(experimentHelpers.getJobAttemptsByJob(tj.base.id));
		}

		return atts.stream()
				.filter(att -> status.contains(att.status))
				.map(att -> att.create(this, jobs.get(att.jobId)))
				.collect(Collectors.toList());
	}

	@Override
	public synchronized TempJobAttempt getJobAttempt(TempJobAttempt.Impl att) throws SQLException {
		return experimentHelpers.getJobAttempt(att.base.id);
	}

	@Override
	public synchronized Map<TempJob.Impl, List<TempJobAttempt.Impl>> filterJobAttempts(TempExperiment.Impl exp, EnumSet<JobAttempt.Status> status) throws SQLException {
		List<TempJobAttempt> atts = experimentHelpers.filterJobAttemptsByExperiment(exp.base.id, status);

		long[] jids = atts.stream().map(att -> att.jobId).collect(Collectors.toSet()).stream()
				.mapToLong(j -> j)
				.toArray();

		Map<Long, TempJob.Impl> jobs = experimentHelpers.getJobsById(jids).stream()
				.map(j -> j.create(exp))
				.collect(Collectors.toMap(j -> j.base.id, j -> j));

		return NimrodUtils.mapToParent(
				atts.stream().map(att -> att.create(this, jobs.get(att.jobId))),
				att -> jobs.get(att.base.jobId)
		);
	}

	@Override
	public synchronized TempCommandResult.Impl addCommandResult(TempJobAttempt.Impl att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) throws SQLException {
		return experimentHelpers.addCommandResult(att.base.id, status, index, time, retval, message, errcode, stop).create();
	}

	@Override
	public List<TempCommandResult.Impl> getCommandResultsByAttempt(Map<Long, TempJobAttempt.Impl> attempts) throws SQLException {
		return experimentHelpers.getCommandResultsByAttempt(attempts.keySet()).stream().map(TempCommandResult::create).collect(Collectors.toList());
	}

	@Override
	public synchronized List<NimrodMasterEvent> pollMasterEventsT() throws SQLException {
		List<NimrodMasterEvent> evts = new ArrayList<>();
		try(ResultSet rs = qGetMasterMessages.executeQuery()) {
			while(rs.next()) {
				NimrodMasterEvent evt = eventFromRowT(rs);
				if(evt != null) {
					evts.add(evt);
				}
			}
		}

		qDeleteMasterMessages.executeUpdate();
		return evts;
	}

	@Override
	public synchronized Collection<TempResourceType> getResourceTypeInfo() throws SQLException {
		return resourceHelpers.getResourceTypeInfo();
	}

	@Override
	public synchronized Optional<TempResourceType> getResourceTypeInfo(String name) throws SQLException {
		return resourceHelpers.getResourceTypeInfo(name);
	}

	@Override
	public synchronized TempResourceType addResourceTypeInfo(String name, String clazz) throws SQLException {
		return resourceHelpers.addResourceTypeInfo(name, clazz);
	}

	@Override
	public synchronized boolean deleteResourceTypeInfo(String name) throws SQLException {
		return resourceHelpers.deleteResourceTypeInfo(name);
	}

	@Override
	public synchronized Optional<TempResource.Impl> getResource(String path) throws SQLException {
		return resourceHelpers.getResource(path).map(r -> r.create(this));
	}

	@Override
	public synchronized void deleteResource(TempResource.Impl node) throws SQLException {
		resourceHelpers.deleteResource(node.base.id);
	}

	@Override
	public synchronized Collection<TempResource.Impl> getResources() throws SQLException {
		return resourceHelpers.getResources().stream().map(r -> r.create(this)).collect(Collectors.toList());
	}

	@Override
	public synchronized TempResource.Impl addResource(String name, String type, JsonStructure config, NimrodURI amqpUri, NimrodURI txUri) throws SQLException {
		return resourceHelpers.addResource(name, type, config, amqpUri, txUri).create(this);
	}

	@Override
	public synchronized TempResourceType getResourceImplementation(TempResource.Impl node) throws SQLException {
		return resourceHelpers.getResourceTypeInfo(node.base.typeName).orElseThrow(IllegalStateException::new);
	}

	@Override
	public synchronized Collection<TempResource.Impl> getAssignedResources(TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.getAssignedResources(exp.base.id).stream().map(r -> r.create(this)).collect(Collectors.toList());
	}

	@Override
	public synchronized boolean assignResource(TempResource.Impl node, TempExperiment.Impl exp, NimrodURI txUri) throws SQLException {
		return resourceHelpers.assignResource(node.base.id, exp.base.id, txUri);
	}

	@Override
	public synchronized boolean unassignResource(TempResource.Impl node, TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.unassignResource(node.base.id, exp.base.id);
	}

	@Override
	public synchronized Optional<NimrodURI> getAssignmentStatus(TempResource.Impl node, TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.getAssignmentStatus(node.base.id, exp.base.id);
	}

	@Override
	public synchronized boolean isResourceCapable(TempResource.Impl node, TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.isResourceCapable(node.base.id, exp.base.id);
	}

	@Override
	public synchronized boolean addResourceCaps(TempResource.Impl node, TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.addResourceCaps(node.base.id, exp.base.id);
	}

	@Override
	public synchronized boolean removeResourceCaps(TempResource.Impl node, TempExperiment.Impl exp) throws SQLException {
		return resourceHelpers.removeResourceCaps(node.base.id, exp.base.id);
	}

	@Override
	public synchronized Optional<TempAgent.Impl> getAgentInformationByUUID(UUID uuid) throws SQLException {
		return resourceHelpers.getAgentInformationByUUID(uuid).map(TempAgent::create);
	}

	@Override
	public synchronized Optional<TempResource.Impl> getAgentResource(UUID uuid) throws SQLException {
		return resourceHelpers.getAgentResource(uuid).map(r -> r.create(this));
	}

	@Override
	public synchronized Collection<TempAgent.Impl> getResourceAgentInformation(TempResource.Impl node) throws SQLException {
		return resourceHelpers.getResourceAgentInformation(node.base.id).stream().map(TempAgent::create).collect(Collectors.toList());
	}

	@Override
	public synchronized AgentState addAgent(TempResource.Impl node, AgentState agent) throws SQLException {
		return resourceHelpers.addAgent(node.base.id, agent).create();
	}

	@Override
	public synchronized void updateAgent(AgentState agent) throws SQLException {
		resourceHelpers.updateAgent(agent);
	}

	@Override
	public synchronized boolean addAgentPlatform(String platformString, Path path) throws SQLException {
		return resourceHelpers.addAgentPlatform(platformString, path);
	}

	@Override
	public synchronized boolean deleteAgentPlatform(String platformString) throws SQLException {
		return resourceHelpers.deleteAgentPlatform(platformString);
	}

	@Override
	public synchronized boolean mapAgentPosixPlatform(String platformString, String system, String machine) throws SQLException {
		return resourceHelpers.mapAgentPosixPlatform(platformString, system, machine);
	}

	@Override
	public synchronized boolean unmapAgentPosixPlatform(String system, String machine) throws SQLException {
		return resourceHelpers.unmapAgentPosixPlatform(system, machine);
	}

	@Override
	protected NimrodException.DbError makeException(SQLException e) {
		return new NimrodException.DbError(e);
	}

	@Override
	public void close() throws SQLException {
		for(PreparedStatement ps : statements) {
			ps.close();
		}
	}

	private void addConfigChangeMessage(String key, String _old, String _new) throws SQLException {
		if(_old == null && _new == null) {
			return;
		} else if(_old == null && _new != null) {
			qAddMasterMessage.setString(1, "INSERT");
		} else if(_old != null && _new != null) {
			qAddMasterMessage.setString(1, "UPDATE");
		} else if(_old != null && _new == null) {
			qAddMasterMessage.setString(1, "DELETE");
		}

		qAddMasterMessage.setString(2, "config");
		qAddMasterMessage.setString(3, Json.createObjectBuilder()
				.add("key", key)
				.add("old", _old == null ? JsonValue.NULL : Json.createValue(_old))
				.add("value", _new == null ? JsonValue.NULL : Json.createValue(_new))
				.build().toString());

		qAddMasterMessage.executeUpdate();
	}

	private void addJobMessage(long jobId, long expId, long jobIndex, Instant created) throws SQLException {
		qAddMasterMessage.setString(1, "INSERT");
		qAddMasterMessage.setString(2, "job");
		qAddMasterMessage.setString(3, Json.createObjectBuilder()
				.add("id", jobId)
				.add("exp_id", expId)
				.add("job_index", jobIndex)
				.add("created", created.toEpochMilli() / 1000)
				.build().toString());
		qAddMasterMessage.executeUpdate();
	}

	private NimrodMasterEvent eventFromRowT(ResultSet rs) throws SQLException {
		String op = rs.getString("operation");
		String clazz = rs.getString("class");
		Instant ts = DBUtils.getLongInstant(rs, "ts");
		JsonObject payload = DBUtils.getJSONObject(rs, "payload");

		switch(clazz) {
			case "config":
				return new ConfigChangeMasterEvent(payload.getString("key"), DBUtils.jsonStringOrNull(payload, "old"), DBUtils.jsonStringOrNull(payload, "value"));
			case "job": {
				/* Sometimes we get stale messages */
				Optional<TempExperiment.Impl> exp = getExperiment(payload.getInt("exp_id"));
				if(exp.isEmpty()) {
					return null;
				}

				Optional<TempJob.Impl> job = getSingleJobT(payload.getInt("id"));
				return job.map(impl -> new JobAddMasterEvent(exp.get(), impl)).orElse(null);
			}
		}

		return null;
	}

}
