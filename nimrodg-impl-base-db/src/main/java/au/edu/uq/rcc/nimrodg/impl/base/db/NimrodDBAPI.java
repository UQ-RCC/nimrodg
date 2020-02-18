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

import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodEntity;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public interface NimrodDBAPI extends ResourceFunctions<TempResource.Impl, TempExperiment.Impl, TempAgent.Impl, SQLException>, ISQLBase<NimrodException.DbError>, AutoCloseable {

	NimrodConfig getConfig() throws SQLException;

	NimrodConfig updateConfig(String workDir, String storeDir, NimrodURI amqpUri, String amqpRoutingKey, NimrodURI txUri) throws SQLException;

	String getProperty(String key) throws SQLException;

	String setProperty(String key, String value) throws SQLException;

	Map<String, String> getProperties() throws SQLException;

	Map<String, TempAgentInfo.Impl> lookupAgents() throws SQLException;

	Optional<TempAgentInfo.Impl> lookupAgentByPlatform(String platform) throws SQLException;

	Optional<TempAgentInfo.Impl> lookupAgentByPOSIX(String system, String machine) throws SQLException;

	TempExperiment.Impl addExperiment(String name, String workDir, String fileToken, CompiledRun r) throws SQLException;

	List<TempExperiment.Impl> listExperiments() throws SQLException;

	Optional<TempExperiment.Impl> getExperiment(String name) throws SQLException;

	Optional<TempExperiment.Impl> getExperiment(long id) throws SQLException;

	boolean deleteExperiment(long id) throws SQLException;

	boolean deleteExperiment(String name) throws SQLException;

	Optional<TempExperiment> getTempExp(long id) throws SQLException;

	void updateExperimentState(TempExperiment.Impl exp, Experiment.State state) throws SQLException;

	Optional<TempJob.Impl> getSingleJobT(long jobId) throws SQLException;

	JobAttempt.Status getJobStatus(TempJob.Impl job) throws SQLException;

	List<TempJob.Impl> filterJobs(TempExperiment.Impl exp, EnumSet<JobAttempt.Status> status, long start, long limit) throws SQLException;

	List<TempJob.Impl> addJobs(TempExperiment.Impl exp, Collection<Map<String, String>> jobs) throws SQLException;

	TempJobAttempt.Impl createJobAttempt(TempJob.Impl job, UUID uuid) throws SQLException;

	void startJobAttempt(TempJobAttempt.Impl att, UUID agentUuid) throws SQLException;

	void finishJobAttempt(TempJobAttempt.Impl att, boolean failed) throws SQLException;

	List<TempJobAttempt.Impl> filterJobAttempts(TempJob.Impl job, EnumSet<JobAttempt.Status> status) throws SQLException;

	TempJobAttempt getJobAttempt(TempJobAttempt.Impl att) throws SQLException;

	Map<TempJob.Impl, List<TempJobAttempt.Impl>> filterJobAttempts(TempExperiment.Impl exp, EnumSet<JobAttempt.Status> status) throws SQLException;

	TempCommandResult.Impl addCommandResult(TempJobAttempt.Impl att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) throws SQLException;

	List<NimrodMasterEvent> pollMasterEventsT() throws SQLException;

	NimrodEntity isTokenValidForStorageT(TempExperiment.Impl exp, String token) throws SQLException;

	@Override
	void close() throws SQLException;
}
