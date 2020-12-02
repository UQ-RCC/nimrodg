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

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.JobCounts;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.events.ConfigChangeMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.impl.base.db.BrokenDBInvariantException;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodDBAPI;
import au.edu.uq.rcc.nimrodg.impl.base.db.SQLUUUUU;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgent;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempAgentInfo;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempCommandResult;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempExperiment;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJob;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJobAttempt;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResource;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempResourceType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import javax.json.JsonStructure;

public class RunDMC extends SQLUUUUU<NimrodException.DbError> implements NimrodDBAPI, AutoCloseable {

	private final Connection conn;
	private final List<PreparedStatement> statements;

	private final PreparedStatement qGetConfig;
	private final PreparedStatement qUpdateConfig;
	private final PreparedStatement qGetProperty;
	private final PreparedStatement qSetProperty;
	private final PreparedStatement qGetProperties;

	/* Event stuff, this needs access to everything so it has to go in here */
	private final PreparedStatement qPollMasterMessages;

	private final DBExperimentHelpers experimentHelpers;
	private final DBAgentHelpers agentHelpers;
	private final DBResourceHelpers resourceHelpers;

	public RunDMC(Connection conn) throws SQLException {
		this.conn = conn;
		this.statements = new ArrayList<>();

		/* Configuration */
		this.qGetConfig = conn.prepareStatement("SELECT * FROM get_config()");
		this.qUpdateConfig = conn.prepareStatement("SELECT * FROM update_config(?, ?, make_uri(?, ?, ?, ?), ?, make_uri(?, ?, ?, ?))");
		this.qGetProperty = conn.prepareStatement("SELECT get_property(?) AS value");
		this.qSetProperty = conn.prepareStatement("SELECT set_property(?, ?) AS value");
		this.qGetProperties = conn.prepareStatement("SELECT * FROM get_properties()");

		this.qPollMasterMessages = conn.prepareStatement("SELECT * FROM poll_master_messages()");

		this.experimentHelpers = new DBExperimentHelpers(conn, statements);
		this.agentHelpers = new DBAgentHelpers(conn, statements);
		this.resourceHelpers = new DBResourceHelpers(conn, statements);
	}

	@Override
	public Connection getConnection() {
		return conn;
	}

	@Override
	public synchronized NimrodConfig getConfig() throws SQLException {
		try(ResultSet rs = qGetConfig.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("get_config() returned no rows.");
			}
			return DBUtils.configFromResultSet(rs).create();
		}
	}

	@Override
	public synchronized NimrodConfig updateConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri) throws SQLException {
		qUpdateConfig.setString(1, workDir);
		qUpdateConfig.setString(2, storeDir);
		DBUtils.setNimrodUri(qUpdateConfig, 3, amqpUri);
		qUpdateConfig.setString(7, amqpRoutingKey);
		DBUtils.setNimrodUri(qUpdateConfig, 8, txUri);

		try(ResultSet rs = qUpdateConfig.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("update_config() returned no rows.");
			}
			return DBUtils.configFromResultSet(rs).create();
		}
	}

	@Override
	public synchronized String getProperty(String key) throws SQLException {
		return DBHelpers.getProperty(key, qGetProperty);
	}

	@Override
	public synchronized String setProperty(String key, String value) throws SQLException {
		return DBHelpers.setProperty(key, value, qSetProperty);
	}

	@Override
	public synchronized Map<String, String> getProperties() throws SQLException {
		return DBHelpers.getProperties(qGetProperties);
	}

	@Override
	public synchronized Map<String, TempAgentInfo.Impl> lookupAgents() throws SQLException {
		return agentHelpers.lookupAgents();
	}

	@Override
	public synchronized Optional<TempAgentInfo.Impl> lookupAgentByPlatform(String platform) throws SQLException {
		return agentHelpers.lookupAgentByPlatform(platform).map(TempAgentInfo::create);
	}

	@Override
	public synchronized Optional<TempAgentInfo.Impl> lookupAgentByPOSIX(String system, String machine) throws SQLException {
		return agentHelpers.lookupAgentByPOSIX(system, machine).map(TempAgentInfo::create);
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
	public synchronized JobCounts getJobCounts(long id) throws SQLException {
		return experimentHelpers.getJobCounts(id);
	}

	@Override
	public synchronized Optional<TempExperiment> getTempExp(long expId) throws SQLException {
		return experimentHelpers.getExperiment(expId);
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

		return Optional.of(job.create(this, exp.get()));
	}

	@Override
	public synchronized JobAttempt.Status getJobStatus(TempJob.Impl job) throws SQLException {
		return experimentHelpers.getJobStatus(job.base.id);
	}

	@Override
	public synchronized List<TempJob.Impl> filterJobs(TempExperiment.Impl exp, EnumSet<JobAttempt.Status> status, long start, long limit) throws SQLException {
		return experimentHelpers.filterJobs(exp.base.id, status, start, limit).stream().map(tj -> tj.create(this, exp)).collect(Collectors.toList());
	}

	@Override
	public synchronized List<TempJob.Impl> addJobs(TempExperiment.Impl exp, Collection<Map<String, String>> vars) throws SQLException {
		return experimentHelpers.addJobs(exp.base.id, vars).stream().map(tj -> tj.create(this, exp)).collect(Collectors.toList());
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
	public synchronized List<TempJobAttempt.Impl> filterJobAttempts(TempJob.Impl job, EnumSet<JobAttempt.Status> status) throws SQLException {
		return experimentHelpers.filterJobAttempts(job.base.id, status).stream()
				.map(att -> att.create(this, job))
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
				.map(j -> j.create(this, exp))
				.collect(Collectors.toMap(j -> j.base.id, j -> j));

		return NimrodUtils.mapToParent(
				atts.stream().map(att -> att.create(this, jobs.get(att.jobId))),
				att -> jobs.get(att.base.jobId)
		);
	}

	@Override
	public synchronized TempCommandResult.Impl addCommandResult(TempJobAttempt.Impl att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) throws SQLException {
		return experimentHelpers.addCommandResult(att.base.id, status, index, time, retval, message, errcode, stop).create(att);
	}

	@Override
	public synchronized List<NimrodMasterEvent> pollMasterEventsT() throws SQLException {
		List<NimrodMasterEvent> evts = new ArrayList<>();
		try(ResultSet rs = qPollMasterMessages.executeQuery()) {
			while(rs.next()) {
				NimrodMasterEvent evt = eventFromRowT(rs);
				if(evt != null) {
					evts.add(evt);
				}
			}
		}

		return evts;
	}

	private NimrodMasterEvent eventFromRowT(ResultSet rs) throws SQLException {
		String op = rs.getString("operation");
		String clazz = rs.getString("class");
		Instant ts = DBUtils.getInstant(rs, "ts");
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

	//------------START OF RESOURCE TREE-------------
	@Override
	public synchronized Collection<Map.Entry<String, String>> getResourceTypeInfo() throws SQLException {
		Collection<TempResourceType> t = resourceHelpers.getResourceTypeInfo();
		return t.stream().map(tt -> new AbstractMap.SimpleImmutableEntry<>(tt.name, tt.clazz)).collect(Collectors.toList());
	}

	@Override
	public synchronized Optional<Map.Entry<String, String>> getResourceTypeInfo(String name) throws SQLException {
		return resourceHelpers.getResourceTypeInfo(name).map(t -> new AbstractMap.SimpleImmutableEntry<>(t.name, t.clazz));
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
	public synchronized ResourceTypeInfo getResourceImplementation(TempResource.Impl node) throws SQLException {
		Class<?> clazz;
		try {
			clazz = Class.forName(node.base.typeClass);
		} catch(ClassNotFoundException e) {
			clazz = null;
		}
		return new ResourceTypeInfo(
				node.base.typeName,
				node.base.typeClass,
				clazz
		);
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
	protected NimrodException.DbError makeException(SQLException e) {
		return new NimrodException.DbError(e);
	}

	@Override
	public void close() throws SQLException {
		for(PreparedStatement ps : statements) {
			ps.close();
		}
	}
}
