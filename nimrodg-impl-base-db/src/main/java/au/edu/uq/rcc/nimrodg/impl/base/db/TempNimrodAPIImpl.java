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

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;

import javax.json.JsonStructure;
import java.io.IOException;
import java.security.cert.Certificate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class TempNimrodAPIImpl implements NimrodAPI, NimrodMasterAPI {

	protected final NimrodDBAPI db;

	public TempNimrodAPIImpl(NimrodDBAPI db) {
		this.db = db;
	}

	public final APICaps API_CAPS = new APICaps(true, true);

	@Override
	public APICaps getAPICaps() {
		return API_CAPS;
	}

	@Override
	public Collection<Experiment> getExperiments() {
		return db.runSQL(() -> Collections.unmodifiableList(db.listExperiments()));
	}

	@Override
	public TempExperiment.Impl getExperiment(String name) {
		return db.runSQL(() -> db.getExperiment(name).orElse(null));
	}

	@Override
	public TempExperiment.Impl addExperiment(String name, CompiledRun ce) {
		return db.runSQLTransaction(() -> {
			Optional<TempExperiment.Impl> exp = db.getExperiment(name);
			if(exp.isPresent()) {
				throw new NimrodException.ExperimentExists(exp.get());
			}

			return db.addExperiment(name, name, ce);
		});
	}

	@Override
	public void deleteExperiment(Experiment _exp) {
		db.runSQL(() -> db.deleteExperiment(validateExperiment(_exp).base.id));
	}

	@Override
	public Job addSingleJob(Experiment exp, Map<String, String> values) {
		return addJobs(exp, List.of(values)).get(0);
	}

	@Override
	public List<Job> addJobs(Experiment exp, Collection<Map<String, String>> jobs) {
		return db.runSQLTransaction(() -> Collections.unmodifiableList(db.addJobs(validateExperiment(exp), jobs)));
	}

	@Override
	public Collection<Job> filterJobs(Experiment exp, EnumSet<JobAttempt.Status> status, long start, int limit) {
		return db.runSQL(() -> Collections.unmodifiableCollection(db.filterJobs(validateExperiment(exp), status, start, limit)));
	}

	@Override
	public List<JobAttempt.Status> getJobStatuses(Collection<Job> jobs) {
		/* TODO: Evaluate changing dbapi calls to take streams instead of lists. */
		return db.runSQL(() -> db.getJobStatuses(jobs.stream().map(TempNimrodAPIImpl::validateJob).collect(Collectors.toList())));
	}

	@Override
	public NimrodConfig getConfig() {
		return db.runSQL(db::getConfig);
	}

	@Override
	public void updateConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri) {
		db.runSQLTransaction(() -> db.updateConfig(workDir, storeDir, amqpUri, amqpRoutingKey, txUri));
	}

	@Override
	public Optional<String> getProperty(String key) {
		return Optional.ofNullable(db.runSQL(() -> db.getProperty(key)));
	}

	@Override
	public Optional<String> setProperty(String key, String value) {
		return Optional.ofNullable(db.runSQLTransaction(() -> db.setProperty(key, value)));
	}

	@Override
	public Map<String, String> getProperties() {
		return db.runSQL(db::getProperties);
	}

	@Override
	public Resource getResource(String resourcePath) {
		return db.runSQL(() -> db.getResource(resourcePath).orElse(null));
	}

	@Override
	public Resource addResource(String name, String type, JsonStructure config, NimrodURI amqpUri, NimrodURI txUri) {
		if(name == null || type == null || config == null) {
			throw new IllegalArgumentException();
		}

		List<String> errors = new ArrayList<>();
		return db.runSQLTransaction(() -> {
			/* Multiple DB hits, but this isn't really a problem right now. */
			ResourceType rt = db.getResourceTypeInfo(type)
					.map(this::createResourceType)
					.orElseThrow(() -> new NimrodException.InvalidResourceType(type));

			if(!rt.validateConfiguration(this, config, errors)) {
				throw new NimrodException.InvalidResourceConfiguration(rt, errors);
			}

			Optional<TempResource.Impl> res = db.getResource(name);
			if(res.isPresent()) {
				throw new NimrodException.ResourceExists(res.get());
			}

			return db.addResource(name, type, config, amqpUri, txUri);
		});
	}

	@Override
	public void deleteResource(Resource _res) {
		db.runSQL(() -> db.deleteResource(validateResource(_res)));
	}

	@Override
	public Collection<Resource> getResources() {
		return db.runSQLTransaction(() -> Collections.unmodifiableCollection(db.getResources()));
	}

	@Override
	public Collection<Resource> getAssignedResources(Experiment _exp) {
		return db.runSQL(() -> Collections.unmodifiableCollection(db.getAssignedResources(validateExperiment(_exp))));
	}

	@Override
	public void assignResource(Resource _res, Experiment _exp, NimrodURI txUri) {
		db.runSQL(() -> db.assignResource(validateResource(_res), validateExperiment(_exp), txUri));
	}

	@Override
	public void unassignResource(Resource _res, Experiment _exp) {
		db.runSQL(() -> db.unassignResource(validateResource(_res), validateExperiment(_exp)));
	}

	@Override
	public Optional<NimrodURI> getAssignmentStatus(Resource res, Experiment exp) {
		return db.runSQL(() -> db.getAssignmentStatus(validateResource(res), validateExperiment(exp)));
	}

	@Override
	public Collection<ResourceType> getResourceTypeInfo() {
		Collection<Map.Entry<String, String>> types = db.runSQL(() -> db.getResourceTypeInfo());

		List<ResourceType> tl = new ArrayList<>();
		for(Map.Entry<String, String> e : types) {
			tl.add(createResourceType(e));
		}
		return tl;
	}

	@Override
	public ResourceType getResourceTypeInfo(String name) {
		return createResourceType(db.runSQL(() -> db.getResourceTypeInfo(name).orElse(null)));
	}

	private ResourceType createResourceType(Map.Entry<String, String> t) {
		if(t == null) {
			return null;
		}

		try {
			return DBUtils.createType(t.getValue());
		} catch(ReflectiveOperationException ex) {
			throw new NimrodException(ex);
		}
	}

	@Override
	public Map<String, AgentInfo> lookupAgents() {
		return db.runSQL(() -> Collections.unmodifiableMap(db.lookupAgents()));
	}

	@Override
	public AgentInfo lookupAgentByPlatform(String platString) {
		return db.runSQL(() -> db.lookupAgentByPlatform(platString).orElse(null));
	}

	@Override
	public AgentInfo lookupAgentByPosix(String system, String machine) {
		return db.runSQL(() -> db.lookupAgentByPOSIX(system, machine).orElse(null));
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource resource, NimrodURI uri, Certificate[] certs) throws IOException {
		ResourceTypeInfo ti = getResourceTypeInfo(resource);

		try {
			/* NB: Using className instead of clazz here for a reason. */
			return DBUtils.createType(ti.className).createActuator(ops, resource, uri, certs);
		} catch(ReflectiveOperationException e) {
			throw new IOException(e);
		}
	}

	private static TempExperiment.Impl validateExperiment(Experiment exp) {
		if(!(exp instanceof TempExperiment.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempExperiment.Impl)exp;
	}

	private static TempResource.Impl validateResource(Resource res) {
		if(!(res instanceof TempResource.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempResource.Impl)res;
	}

	private static TempJob.Impl validateJob(Job job) {
		if(!(job instanceof TempJob.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempJob.Impl)job;
	}

	private static TempJobAttempt.Impl validateJobAttempt(JobAttempt att) {
		if(!(att instanceof TempJobAttempt.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempJobAttempt.Impl)att;
	}

	@Override
	public ResourceTypeInfo getResourceTypeInfo(Resource node) {
		return db.runSQL(() -> db.getResourceImplementation(validateResource(node)));
	}

	@Override
	public boolean isResourceCapable(Resource res, Experiment exp) {
		return db.runSQL(() -> db.isResourceCapable(validateResource(res), validateExperiment(exp)));
	}

	@Override
	public void addResourceCaps(Resource node, Experiment exp) {
		db.runSQL(() -> db.addResourceCaps(validateResource(node), validateExperiment(exp)));
	}

	@Override
	public void removeResourceCaps(Resource node, Experiment exp) {
		db.runSQL(() -> db.removeResourceCaps(validateResource(node), validateExperiment(exp)));
	}

	@Override
	public AgentState getAgentByUUID(UUID uuid) {
		return db.runSQL(() -> db.getAgentInformationByUUID(uuid).orElse(null));
	}

	@Override
	public Resource getAgentResource(UUID uuid) {
		return db.runSQL(() -> db.getAgentResource(uuid).orElse(null));
	}

	@Override
	public Collection<AgentState> getResourceAgents(Resource node) {
		return db.runSQL(() -> Collections.unmodifiableCollection(db.getResourceAgentInformation(validateResource(node))));
	}

	@Override
	public AgentState addAgent(Resource node, AgentState agent) {
		return db.runSQL(() -> db.addAgent(validateResource(node), agent));
	}

	@Override
	public void updateAgent(AgentState agent) {
		db.runSQL(() -> db.updateAgent(agent));
	}

	@Override
	public void updateExperimentState(Experiment _exp, Experiment.State state) {
		db.runSQL(() -> db.updateExperimentState(validateExperiment(_exp), state));
	}

	@Override
	public List<JobAttempt> createJobAttempts(Collection<Job> jobs) {
		List<JobAttempt> atts = new ArrayList<>(jobs.size());
		db.runSQLTransaction(() -> {
			for(Job j : jobs) {
				atts.add(db.createJobAttempt(validateJob(j), UUID.randomUUID()));
			}
		});
		return atts;
	}

	@Override
	public void startJobAttempt(JobAttempt att, UUID agentUuid) {
		db.runSQL(() -> db.startJobAttempt(validateJobAttempt(att), agentUuid));
	}

	@Override
	public void finishJobAttempt(JobAttempt att, boolean failed) {
		db.runSQL(() -> db.finishJobAttempt(validateJobAttempt(att), failed));
	}

	@Override
	public Map<Job, Collection<JobAttempt>> filterJobAttempts(Experiment _exp, EnumSet<JobAttempt.Status> status) {
		return db.runSQLTransaction(() -> db.filterJobAttempts(validateExperiment(_exp), status))
				.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableCollection(e.getValue())));
	}

	@Override
	public CommandResult addCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) {
		return db.runSQLTransaction(() -> db.addCommandResult(validateJobAttempt(att), status, index, time, retval, message, errcode, stop));
	}

	@Override
	public Collection<NimrodMasterEvent> pollMasterEvents() {
		/* Holy hell, don't run this outside of a transaction. */
		return db.runSQLTransaction(db::pollMasterEventsT);
	}

	@Override
	public void close() {
		try {
			db.close();
		} catch(SQLException e) {
			throw new NimrodException.DbError(e);
		}
	}

	public abstract Connection getConnection();
}
