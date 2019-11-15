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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import javax.json.JsonStructure;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodEntity;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodServeAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class TempNimrodAPIImpl implements NimrodAPI, NimrodMasterAPI, NimrodServeAPI {

	protected final NimrodDBAPI db;

	public TempNimrodAPIImpl(NimrodDBAPI db) throws SQLException {
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
		if(db.runSQL(() -> db.experimentExists(name))) {
			throw new ExperimentExistsException(name);
		}

		/* FIXME: I feel that this shouldn't be here. */
		Path rootStore = Paths.get(this.getConfig().getRootStore());
		Path workDir = rootStore.resolve(name);

		try {
			if(Files.exists(workDir)) {
				NimrodUtils.deltree(workDir);
			}

			Files.createDirectories(workDir);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		return db.runSQLTransaction(() -> db.addExperiment(name, name, null, ce));
	}

	@Override
	public void deleteExperiment(Experiment _exp) throws IOException {
		TempExperiment.Impl exp = validateExperiment(_exp);

		/* FIXME: This shouldn't be here. */
		Path workDir = Paths.get(getConfig().getRootStore()).resolve(exp.getWorkingDirectory());
		NimrodUtils.deltree(workDir);

		db.runSQL(() -> db.deleteExperiment(exp.base.id));
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
	public NimrodConfig getConfig() {
		return db.runSQL(() -> db.getConfig());
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
		return db.runSQL(() -> db.getProperties());
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
		// TODO: Validate configuration.
		return db.runSQL(() -> db.addResource(name, type, config, amqpUri, txUri));
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
	public void assignResource(Resource _res, Experiment _exp, Optional<NimrodURI> txUri) {
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
			ResourceType t;
			try {
				tl.add(DBUtils.createType(e.getValue()));
			} catch(ReflectiveOperationException ex) {
				throw new NimrodAPIException(ex);
			}
		}
		return tl;
	}

	@Override
	public ResourceType getResourceTypeInfo(String name) {
		Map.Entry<String, String> t = db.runSQL(() -> db.getResourceTypeInfo(name).orElse(null));
		if(t == null) {
			return null;
		}

		try {
			return DBUtils.createType(t.getValue());
		} catch(ReflectiveOperationException ex) {
			throw new NimrodAPIException(ex);
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
		if(exp == null || !(exp instanceof TempExperiment.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempExperiment.Impl)exp;
	}

	private static TempResource.Impl validateResource(Resource res) {
		if(res == null || !(res instanceof TempResource.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempResource.Impl)res;
	}

	private static TempJob.Impl validateJob(Job job) {
		if(job == null || !(job instanceof TempJob.Impl)) {
			throw new IllegalArgumentException();
		}

		return (TempJob.Impl)job;
	}

	private static TempJobAttempt.Impl validateJobAttempt(JobAttempt att) {
		if(att == null || !(att instanceof TempJobAttempt.Impl)) {
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
	public String getJobAttemptToken(JobAttempt _att) {
		TempJobAttempt.Impl att = validateJobAttempt(_att);
		return att.getToken();
	}

	@Override
	public Map<Job, Collection<JobAttempt>> filterJobAttempts(Experiment _exp, EnumSet<JobAttempt.Status> status) {
		return db.runSQLTransaction(() -> db.filterJobAttempts(validateExperiment(_exp), status))
				.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> Collections.unmodifiableCollection(e.getValue())));
	}

	@Override
	public CommandResult addCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) {
		return db.runSQL(() -> db.addCommandResult(validateJobAttempt(att), status, index, time, retval, message, errcode, stop));
	}

	@Override
	public Collection<NimrodMasterEvent> pollMasterEvents() {
		/* Holy hell, don't run this outside of a transaction. */
		return db.runSQLTransaction(() -> db.pollMasterEventsT());
	}

	@Override
	public void close() throws SQLException {
		db.close();
	}

	@Override
	public NimrodEntity isTokenValidForStorage(Experiment _exp, String token) {
		TempExperiment.Impl run = validateExperiment(_exp);
		if(token == null) {
			return null;
		}

		return db.runSQLTransaction(() -> db.isTokenValidForStorageT(run, token));
	}
}
