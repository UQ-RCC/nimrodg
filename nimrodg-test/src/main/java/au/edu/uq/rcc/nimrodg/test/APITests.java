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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.ActuatorOpsAdapter;
import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.MachinePair;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.api.setup.AMQPConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.TransferConfigBuilder;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class APITests {

	protected abstract NimrodAPI getNimrod();
	protected abstract Path getRoot();

	protected NimrodMasterAPI getNimrodMasterAPI() {
		NimrodAPI nimrod = getNimrod();
		Assertions.assertTrue(nimrod.getAPICaps().master);
		return (NimrodMasterAPI)nimrod;
	}

	@Test
	public void getNonExistentExperimentTestw() {
		Assertions.assertNull(getNimrod().getExperiment("asdfasdf"));
	}

	@Test
	public void experimentEnumerationTest() throws NimrodException, RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("test1", TestUtils.getSampleExperiment());
		Experiment exp2 = api.addExperiment("test2", TestUtils.getSampleExperiment());
		Experiment exp3 = api.addExperiment("test3", TestUtils.getSampleExperiment());
		Experiment exp4 = api.addExperiment("test4", TestUtils.getSampleExperiment());

		Experiment[] exp = api.getExperiments().stream().toArray(Experiment[]::new);
		Assertions.assertArrayEquals(new Experiment[]{exp1, exp2, exp3, exp4}, exp);
	}

	@Test
	public void basicTests() throws NimrodException, RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Experiment exp2 = api.getExperiment("test1");

		Assertions.assertEquals(exp, exp2);

		exp.getTasks();

		Job newJob = api.addSingleJob(exp, Map.of("x", "xxx", "y", "yyy"));
		Assertions.assertEquals(Map.of(
				"x", "xxx",
				"y", "yyy",
				"jobindex", "3",
				"jobname", "3"
		), newJob.getVariables());

		List<Job> jobs = new ArrayList<>(api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 0));

		Assertions.assertEquals(newJob, jobs.get(2));

		Resource node = api.addResource("test", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		api.assignResource(node, exp);

		List<Resource> ass = new ArrayList<>(api.getAssignedResources(exp));

		Assertions.assertEquals(node, ass.get(0));
	}

	@Test
	public void runNotReturningImplicitVariablesTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Collection<String> vars = exp.getVariables();

		/* Make sure we don't contain any of our implicit variables. */
		Assertions.assertEquals(2, vars.size());
		Assertions.assertTrue(vars.contains("x"));
		Assertions.assertTrue(vars.contains("y"));

	}

	/**
	 * https://github.com/UQ-RCC/nimrodg/issues/38
	 */
	@Test
	public void githubIssue38Test() throws RunfileBuildException {
		NimrodMasterAPI api = (NimrodMasterAPI)getNimrod();

		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		{
			JobAttempt att = api.createJobAttempts(List.of(j)).get(0);

			CommandResult cr1 = api.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS,
					1, 10.0f, 0, "Success", 0, true);

			Assertions.assertEquals(1, cr1.getIndex());

			CommandResult cr2 = api.addCommandResult(att, CommandResult.CommandResultStatus.ABORTED,
					-1, 0.0f, 0, "", 0, true);

			Assertions.assertEquals(2, cr2.getIndex());
		}
	}

	@Test
	public void jobAttemptTests() throws RunfileBuildException, PlanfileParseException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		UUID agentUuid = UUID.randomUUID();
		List<JobAttempt> attempts = new ArrayList<>();
		/* Create one job a attempt and make it run successfully. */
		{
			JobAttempt att = api.createJobAttempts(List.of(j)).get(0);

			Assertions.assertNull(att.getAgentUUID());
			Assertions.assertNull(att.getStartTime());
			Assertions.assertNull(att.getFinishTime());
			Assertions.assertEquals(JobAttempt.Status.NOT_RUN, att.getStatus());
			Assertions.assertEquals(JobAttempt.Status.NOT_RUN, api.getJobStatus(j));

			api.startJobAttempt(att, agentUuid);

			Assertions.assertEquals(agentUuid, att.getAgentUUID());
			Assertions.assertNotNull(att.getStartTime());
			Assertions.assertNull(att.getFinishTime());
			Assertions.assertEquals(JobAttempt.Status.RUNNING, att.getStatus());
			Assertions.assertEquals(JobAttempt.Status.RUNNING, api.getJobStatus(j));

			/* Add a command result for goot measure. Note this has no affect on the attempt status. */
			api.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS, 1, 10.0f, 0, "Success", 0, true);

			api.finishJobAttempt(att, false);
			Assertions.assertEquals(agentUuid, att.getAgentUUID());
			Assertions.assertNotNull(att.getStartTime());
			Assertions.assertNotNull(att.getFinishTime());
			Assertions.assertEquals(JobAttempt.Status.COMPLETED, att.getStatus());
			Assertions.assertEquals(JobAttempt.Status.COMPLETED, api.getJobStatus(j));

			attempts.add(att);
		}

		/* Create another attempt and fail it immediately */
		{
			JobAttempt att = api.createJobAttempts(List.of(j)).get(0);

			Assertions.assertNull(att.getAgentUUID());
			Assertions.assertNull(att.getStartTime());
			Assertions.assertNull(att.getFinishTime());
			Assertions.assertEquals(JobAttempt.Status.NOT_RUN, att.getStatus());
			Assertions.assertEquals(JobAttempt.Status.COMPLETED, api.getJobStatus(j));

			api.finishJobAttempt(att, true);

			Assertions.assertNull(att.getAgentUUID());
			Assertions.assertNotNull(att.getStartTime());
			Assertions.assertNotNull(att.getFinishTime());
			Assertions.assertEquals(JobAttempt.Status.FAILED, att.getStatus());
			Assertions.assertEquals(JobAttempt.Status.COMPLETED, api.getJobStatus(j));

			attempts.add(att);
		}

		{
			/* Create an attempt and leave it NOT_RUN. */
			JobAttempt notRunAtt = api.createJobAttempts(List.of(j)).get(0);
			attempts.add(notRunAtt);

			/* Create an attempt and leave it RUNNING. */
			JobAttempt runningAtt = api.createJobAttempts(List.of(j)).get(0);
			api.startJobAttempt(runningAtt, agentUuid);
			attempts.add(runningAtt);

			/* Check filtering everything. */
			Set<JobAttempt> js0 = new HashSet<>(attempts);
			Set<JobAttempt> js1 = new HashSet<>(api.filterJobAttempts(j, EnumSet.allOf(JobAttempt.Status.class)));

			Assertions.assertEquals(js0, js1);

			/* See if we can filter the NOT_RUN attempt. */
			Assertions.assertEquals(notRunAtt, api.filterJobAttempts(j, EnumSet.of(JobAttempt.Status.NOT_RUN)).stream()
					.findFirst().orElseThrow(IllegalStateException::new));

			/* See if we can filter the RUNNING attempt. */
			Assertions.assertEquals(runningAtt, api.filterJobAttempts(j, EnumSet.of(JobAttempt.Status.RUNNING)).stream()
					.findFirst().orElseThrow(IllegalStateException::new));
		}

		{
			/* Check filtering by experiment. Used by the master. */
			Map<Job, Set<JobAttempt>> j0 = Map.of(j, new HashSet<>(attempts));
			Map<Job, Set<JobAttempt>> j1 = api.filterJobAttempts(exp, EnumSet.allOf(JobAttempt.Status.class)).entrySet().stream()
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							e -> new HashSet<>(e.getValue())
					));
			Assertions.assertEquals(j0, j1);
		}
	}

	@Test
	public void commandResultTest1() throws RunfileBuildException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		UUID agentUuid = UUID.randomUUID();
		JobAttempt att = api.createJobAttempts(List.of(j)).get(0);

		api.startJobAttempt(att, agentUuid);

		/* Try adding a command result for a non-existent command. */
		Assertions.assertThrows(NimrodException.DbError.class, () -> api.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS, 100, 10.0f, 0, "Success", 0, true));
	}

	@Test
	public void commandResultTest2() throws RunfileBuildException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		UUID agentUuid = UUID.randomUUID();
		JobAttempt att = api.createJobAttempts(List.of(j)).get(0);

		api.startJobAttempt(att, agentUuid);

		/* Try adding the command result twice. */
		api.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true);

		Assertions.assertThrows(NimrodException.DbError.class, () -> api.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true));
	}

	@Test
	public void commandResultTest3() throws RunfileBuildException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		UUID[] agentUuids = new UUID[2];
		for(int i = 0; i < agentUuids.length; ++i) {
			agentUuids[i] = UUID.randomUUID();
		}

		JobAttempt att1 = api.createJobAttempts(List.of(j)).get(0);
		JobAttempt att2 = api.createJobAttempts(List.of(j)).get(0);

		api.startJobAttempt(att1, agentUuids[0]);
		api.addCommandResult(att1, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true);

		api.startJobAttempt(att2, agentUuids[1]);

		/* This used to throw "java.sql.SQLException: No such command". */
		api.addCommandResult(att2, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true);
	}

	@Test
	public void commandResultTest4() throws RunfileBuildException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job j = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		UUID[] agentUuids = new UUID[2];
		for(int i = 0; i < agentUuids.length; ++i) {
			agentUuids[i] = UUID.randomUUID();
		}

		JobAttempt att1 = api.createJobAttempts(List.of(j)).get(0);
		JobAttempt att2 = api.createJobAttempts(List.of(j)).get(0);

		api.startJobAttempt(att1, agentUuids[0]);
		CommandResult att1cr1 = api.addCommandResult(att1, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true);

		api.startJobAttempt(att2, agentUuids[1]);

		/* This used to throw "java.sql.SQLException: No such command". */
		CommandResult att2cr1 = api.addCommandResult(att2, CommandResult.CommandResultStatus.SUCCESS, 0, 10.0f, 0, "Success", 0, true);

		Map<JobAttempt, List<CommandResult>> crs = api.getCommandResults(List.of(att1, att2));
		Assertions.assertEquals(Map.of(
				att1, List.of(att1cr1),
				att2, List.of(att2cr1)
		), crs);
	}


	@Test
	public void substitutionApplicationTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job job = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		NetworkJob nj = MsgUtils.resolveJob(UUID.randomUUID(), job, Task.Name.Main, URI.create("http://localhost"));

		NetworkJob.ExecCommand cmd = (NetworkJob.ExecCommand)nj.commands.get(0);

		Assertions.assertEquals("echo value-x-0 value-y-0 1 1", cmd.arguments.get(0));
	}

	@Test
	public void benchSubstitutionTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		CompiledRun cr = TestUtils.getBenchRun();

		Experiment exp = api.addExperiment("testbench1", cr);

		Job job = api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream()
				.findFirst().orElseThrow(IllegalStateException::new);

		NetworkJob nj = MsgUtils.resolveJob(UUID.randomUUID(), job, Task.Name.Main, URI.create("http://localhost"));

		NetworkJob.ExecCommand cmd = (NetworkJob.ExecCommand)nj.commands.get(1);
		Assertions.assertEquals("/home/uqzvanim/nimbench.sh GET 1kb", cmd.arguments.get(0));
	}

	private static class _FactuatorOps extends ActuatorOpsAdapter {

		public _FactuatorOps(NimrodMasterAPI nimrod) {
			super(nimrod);
		}

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentInfo.ShutdownReason reason, int signal) throws IllegalArgumentException {

		}

		@Override
		public String getSigningAlgorithm() {
			return "NIM1-HMAC-NULL";
		}
	}

	private DummyActuator createDummyActuator(Resource res, NimrodMasterAPI mapi) {
		try {
			return (DummyActuator)mapi.createActuator(new _FactuatorOps(mapi), res, NimrodURI.create(URI.create("amqp://dummy-server/vhost"), "/not/a/path", true, true), new Certificate[]{});
		} catch(IOException e) {
			/* Won't happen, but whatever. */
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void agentSecretKeyTest() {
		NimrodMasterAPI mapi = (NimrodMasterAPI)getNimrod();

		Resource res = mapi.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		DefaultAgentState as = new DefaultAgentState();
		ReferenceAgent ra = new ReferenceAgent(as, new NoopAgentListener());

		/* Don't specify a secret key, one should be generated. */
		ra.reset(UUID.randomUUID());
		AgentState nas = mapi.addAgent(res, as);
		as.update(nas);
		Assertions.assertNotNull(as.getSecretKey());

		ra.disconnect(AgentInfo.ShutdownReason.HostSignal, 9);

		/* Use a specific key. */
		ra.reset(UUID.randomUUID(), "abc123");
		nas = mapi.addAgent(res, as);
		as.update(nas);
		Assertions.assertEquals("abc123", as.getSecretKey());
	}

	@Test
	public void setActuatorDataAfterAddTest() {
		NimrodMasterAPI mapi = getNimrodMasterAPI();

		Resource rootResource = mapi.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		DefaultAgentState as = new DefaultAgentState();

		ReferenceAgent ra = new ReferenceAgent(as, new NoopAgentListener());
		ra.reset(UUID.randomUUID());

		mapi.addAgent(rootResource, as);

		Assertions.assertNull(as.getActuatorData());

		as.setActuatorData(Json.createObjectBuilder()
				.add("x", 0)
				.build());

		mapi.updateAgent(as);

		AgentState as2 = mapi.getAgentByUUID(as.getUUID());
		Assertions.assertEquals(as.getActuatorData(), as2.getActuatorData());
	}

	@Test
	public void fakeAgentTests() throws IllegalArgumentException, IOException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		//onPaperAssignmentTest();

		Resource rootResource = api.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		/*
		 * Get the actuator to "launch" agents.
		 * It really just creates a bunch of agent.hello messages, which is the same thing, really.
		 */
		List<AgentHello> hellos;
		try(DummyActuator act = createDummyActuator(rootResource, api)) {
			Actuator.Request[] requests = new Actuator.Request[10];
			for(int i = 0; i < requests.length; ++i) {
				requests[i] = Actuator.Request.forAgent(UUID.randomUUID(), "secret");
			}

			act.launchAgents(requests);

			hellos = act.simulateHellos();

			ReferenceAgent[] agents = new ReferenceAgent[hellos.size()];

			FakeAgentListener l = new FakeAgentListener(api, act);

			for(int i = 0; i < agents.length; ++i) {
				DefaultAgentState as = new DefaultAgentState();

				agents[i] = new ReferenceAgent(as, l);
				as.setActuatorData(Json.createObjectBuilder()
						.add("hashCode", as.hashCode())
						.build());

				agents[i].reset(requests[i].uuid);
				agents[i].processMessage(hellos.get(i), Instant.now());

				Assertions.assertEquals(
						Json.createObjectBuilder()
								.add("hashCode", agents[i].getDataStore().hashCode())
								.build(),
						agents[i].getDataStore().getActuatorData()
				);
			}

			for(int i = 0; i < agents.length; ++i) {
				ReferenceAgent ra = agents[i];
				ra.processMessage(new AgentShutdown.Builder()
						.agentUuid(requests[i].uuid)
						.timestamp(Instant.now())
						.reason(AgentInfo.ShutdownReason.HostSignal)
						.signal(15)
						.build(), Instant.now());
			}
		}
	}

	/**
	 * Simulate an agent being launched, but with the application being terminated before it's connected.
	 *
	 * @throws java.io.IOException For good measure.
	 */
	@Test
	public void agentWaitingForHelloToShutdownTest() throws IOException {
		NimrodMasterAPI api = getNimrodMasterAPI();

		Resource rootResource = api.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		UUID uuid = UUID.randomUUID();

		try(DummyActuator act = createDummyActuator(rootResource, api)) {
			FakeAgentListener l = new FakeAgentListener(api, act);

			Actuator.LaunchResult lr = act.launchAgents(Actuator.Request.forAgent(uuid, "secret"))[0];
			Assertions.assertNull(lr.t);

			DefaultAgentState as = new DefaultAgentState();
			ReferenceAgent ra = new ReferenceAgent(as, l);
			as.setActuatorData(JsonObject.EMPTY_JSON_OBJECT);
			ra.reset(uuid);

			ra.terminate();
		}

		/* This is a new instance. */
		AgentState as = api.getAgentByUUID(uuid);

		Assertions.assertEquals(AgentInfo.State.SHUTDOWN, as.getState());
		Assertions.assertNull(as.getQueue());
		Assertions.assertTrue(as.getExpired());
		Assertions.assertEquals(AgentInfo.ShutdownReason.Requested, as.getShutdownReason());
		Assertions.assertEquals(-1, as.getShutdownSignal());
	}

	@Test
	public void resourceDeletionWhenAssignedTest() throws RunfileBuildException {
		NimrodAPI api = getNimrod();
		TestUtils.createSampleResources(api);

		Resource root = api.getResource("root");

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());
		api.assignResource(root, exp1);

		Assertions.assertThrows(NimrodException.DbError.class, () -> api.deleteResource(root));
	}

	@Test
	public void complexResourceAssignmentTest() throws NimrodException, RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		TestUtils.createSampleResources(api);

		Resource root = api.getResource("root");
		Resource tinaroo = api.getResource("tinaroo");
		Resource awoonga = api.getResource("awoonga");
		Resource flashlite = api.getResource("flashlite");
		Resource nectar = api.getResource("nectar");

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());
		Experiment exp2 = api.addExperiment("exp2", TestUtils.getSimpleSampleEmptyExperiment());
		Experiment exp3 = api.addExperiment("exp3", TestUtils.getSimpleSampleEmptyExperiment());

		/* Assign "root" to everything */
		api.assignResource(root, exp1);
		api.assignResource(root, exp2);
		api.assignResource(root, exp3);

		api.assignResource(flashlite, exp1);

		Collection<Resource> roots = api.getResources();
		Assertions.assertEquals(5, roots.size());
		Assertions.assertTrue(roots.contains(root));
		Assertions.assertTrue(roots.contains(tinaroo));
		Assertions.assertTrue(roots.contains(awoonga));
		Assertions.assertTrue(roots.contains(flashlite));
		Assertions.assertTrue(roots.contains(nectar));

		{
			Collection<Resource> res = api.getAssignedResources(exp1);
			Assertions.assertTrue(res.contains(root));
			Assertions.assertTrue(res.contains(flashlite));
		}

		{
			Collection<Resource> res = api.getAssignedResources(exp2);
			Assertions.assertTrue(res.contains(root));
		}

		{
			Collection<Resource> res = api.getAssignedResources(exp3);
			Assertions.assertTrue(res.contains(root));
		}
	}

	@Test
	public void testCapabilityTest() throws NimrodException, RunfileBuildException, PlanfileParseException {
		NimrodMasterAPI api = getNimrodMasterAPI();
		TestUtils.createSampleResources(api);

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSampleExperiment());

		Experiment exp2 = api.addExperiment("exp2", TestUtils.getSampleExperiment());

		/* Assign tinaroo to exp1 */
		Resource tinaroo = api.getResource("tinaroo");
		api.assignResource(tinaroo, exp1);

		/* Everything should be incapable initially. */
		Assertions.assertFalse(api.isResourceCapable(tinaroo, exp1));
		Assertions.assertFalse(api.isResourceCapable(tinaroo, exp2));

		/* Make tinaroo capable of exp1. */
		api.addResourceCaps(tinaroo, exp1);
		Assertions.assertTrue(api.isResourceCapable(tinaroo, exp1));

		/* tinaroo isn't assigned to exp2, so this shouldn't do anything */
		api.addResourceCaps(tinaroo, exp2);
		Assertions.assertTrue(api.isResourceCapable(tinaroo, exp2));

		/* Unassign tinaroo from exp1 and check its capability was removed */
		api.unassignResource(tinaroo, exp1);
		Assertions.assertFalse(api.isResourceCapable(tinaroo, exp1));
	}

	@Test
	public void masterJobMessageTests() throws RunfileBuildException, PlanfileParseException {
		NimrodMasterAPI api = getNimrodMasterAPI();

		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		/* Drain any initial config change events. */
		api.pollMasterEvents();

		/* A job added to a stopped run shouldn't generate an event. */
		Assertions.assertEquals(Experiment.State.STOPPED, exp.getState());
		api.addSingleJob(exp, Map.of("x", "xx", "y", "yy"));

		Collection<NimrodMasterEvent> _evts = api.pollMasterEvents();
		Assertions.assertEquals(0, _evts.size());

		/* Start the experiment, this should cause an event to be created. */
		api.updateExperimentState(exp, Experiment.State.STARTED);
		api.addSingleJob(exp, Map.of("x", "xx", "y", "yy"));

		List<NimrodMasterEvent> evts = new ArrayList<>(api.pollMasterEvents());
		Assertions.assertEquals(1, evts.size());

		{
			NimrodMasterEvent nme = evts.get(0);
			Assertions.assertEquals(NimrodMasterEvent.Type.JobAdd, nme.getType());

			JobAddMasterEvent ja = (JobAddMasterEvent)nme;
			Assertions.assertEquals(exp, ja.exp);
		}
	}

	@Test
	public void multipleExperimentsTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		api.addExperiment("exp1", TestUtils.getSampleExperiment());
		api.addExperiment("exp2", TestUtils.getSampleExperiment());
	}

	@Test
	public void commandNormalisationTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSampleExperiment());

		exp1.getTasks().values().stream()
				.flatMap(t -> t.getCommands().stream())
				.forEach(cmd -> {});
	}

	@Test
	public void multiJobTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());

		Assertions.assertEquals(Set.of("x", "y"), exp1.getVariables());
		Assertions.assertTrue(api.filterJobs(exp1, EnumSet.allOf(JobAttempt.Status.class), 0, 0).isEmpty());
	}

	@Test
	public void agentPlatformTests() {
		NimrodAPI api = getNimrod();

		AgentDefinition ai = api.lookupAgentByPlatform("x86_64-pc-linux-musl");
		Assertions.assertNotNull(ai);
		Assertions.assertEquals("x86_64-pc-linux-musl", ai.getPlatformString());

		Assertions.assertEquals(Set.of(MachinePair.of("Linux", "x86_64"), MachinePair.of("Linux", "k10m")), ai.posixMappings());

		AgentDefinition ai2 = api.lookupAgentByPosix(MachinePair.of("Linux", "x86_64"));
		Assertions.assertNotNull(ai2);
		Assertions.assertEquals(ai, ai2);

		ai2 = api.lookupAgentByPosix(MachinePair.of("Linux", "k10m"));
		Assertions.assertNotNull(ai2);
		Assertions.assertEquals(ai, ai2);

		AgentDefinition noop = api.lookupAgentByPlatform("noop");
		Assertions.assertTrue(api.lookupAgents().containsKey(noop.getPlatformString()));
		Assertions.assertTrue(api.deleteAgentPlatform(noop.getPlatformString()));
		Assertions.assertFalse(api.lookupAgents().containsKey(noop.getPlatformString()));
	}

	@Test
	public void assignmentStateTests() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleExperiment());

		/* Ensure we're a directory. */
		Assertions.assertTrue(exp1.getWorkingDirectory().endsWith("/"));

		Resource res = api.addResource("test1", "dummy", JsonValue.EMPTY_JSON_OBJECT, null,
				NimrodURI.create(URI.create("file:///path/to/storage/root/"), null, null, null)
		);

		/* Test a with a custom experiment mapping. */
		{
			api.assignResource(res, exp1, NimrodURI.create(URI.create("file:///some/other/path/to/root/"), null, null, null));

			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assertions.assertTrue(nuri.isPresent());

			NimrodURI txUri = nuri.get();
			Assertions.assertEquals(URI.create("file:///some/other/path/to/root/"), txUri.uri.normalize());

			api.unassignResource(res, exp1);
		}

		/* Test with no custom mapping. */
		{
			api.assignResource(res, exp1);
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assertions.assertTrue(nuri.isPresent());

			URI expUri = res.getTransferUri().uri;
			NimrodURI txUri = nuri.get();
			Assertions.assertEquals(expUri, txUri.uri.normalize());

			api.unassignResource(res, exp1);
		}

		/* Test no assignment, with resource override. */
		{
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assertions.assertFalse(nuri.isPresent());
		}

		Resource res2 = api.addResource("test2", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		/* Test assignment, with global fallback. */
		{
			api.assignResource(res2, exp1);
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res2, exp1);
			Assertions.assertTrue(nuri.isPresent());

			URI expUri = api.getConfig().getTransferUri().uri;
			NimrodURI txUri = nuri.get();
			Assertions.assertEquals(expUri, txUri.uri.normalize());

			api.unassignResource(res2, exp1);
		}

		Resource res3 = api.addResource("test3", "dummy", JsonValue.EMPTY_JSON_OBJECT, null,
				NimrodURI.create(URI.create("file:///path/to/storage/root/?key1=val1&key2=val2"), null, null, null)
		);

		/* Test assignment with query, with no  custom mapping */
		{
			api.assignResource(res3, exp1);
			Optional<NimrodURI> uri = api.getAssignmentStatus(res3, exp1);

			Assertions.assertTrue(uri.isPresent());
			Assertions.assertEquals(URI.create("file:/path/to/storage/root/?key1=val1&key2=val2"), uri.get().uri);
		}

		Resource res4 = api.addResource("test4", "dummy", JsonValue.EMPTY_JSON_OBJECT, null,
				NimrodURI.create(URI.create("file:///path/to/storage/root/?key1=val1&key2=val2"), null, null, null)
		);

		{
			URI assUri = URI.create("file:///some/other/path/to/root/?with_parameters=1&more_parameters=yes");
			api.assignResource(res4, exp1, NimrodURI.create(assUri, null, null, null));
			Optional<NimrodURI> uri = api.getAssignmentStatus(res4, exp1);

			Assertions.assertTrue(uri.isPresent());
			Assertions.assertEquals(assUri, uri.get().uri);
		}
	}

	@Test
	public void jobAdditionTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();

		Experiment exp = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());

		List<Job> jobs = new ArrayList<>(api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assertions.assertEquals(0, jobs.size());

		List<Map<String, String>> newJobs = new ArrayList<>();
		api.addJobs(exp, newJobs);

		jobs = new ArrayList<>(api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assertions.assertEquals(0, jobs.size());

		newJobs.add(new HashMap<>() {
			{
				put("x", "0");
				put("y", "1");
			}
		});
		api.addJobs(exp, newJobs);

		jobs = new ArrayList<>(api.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assertions.assertEquals(1, jobs.size());
	}

	@Test
	public void invalidResourceConfigTest() {
		NimrodAPI api = getNimrod();

		/* The dummy resource expects an empty json object. */
		Assertions.assertThrows(NimrodException.InvalidResourceConfiguration.class, () -> api.addResource("test", "dummy", Json.createObjectBuilder()
				.add("x", 1).build(), null, null));
	}

	@Test
	public void invalidResourceTypeTest() {
		NimrodAPI api = getNimrod();
		Assertions.assertThrows(NimrodException.InvalidResourceType.class, () -> api.addResource("test", "dummy2", JsonValue.EMPTY_JSON_OBJECT, null, null));
	}

	@Test
	public void add250000JobsTest() throws RunfileBuildException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		/* This will trigger postgres to batch jobs. */
		api.addExperiment("exp1", TestUtils.get250000Run());
	}

	@Test
	public void updateConfigTest() {
		NimrodAPI api = getNimrod();

		NimrodURI nuri = NimrodURI.create(URI.create("string"), "string", true, true);
		api.updateConfig(
				"string",
				"string",
				nuri,
				"string",
				nuri
		);

		NimrodConfig cfg = api.getConfig();
		Assertions.assertEquals("string", cfg.getWorkDir());
		Assertions.assertEquals("string", cfg.getRootStore());
		Assertions.assertEquals(nuri, cfg.getAmqpUri());
		Assertions.assertEquals(nuri, cfg.getTransferUri());
		Assertions.assertEquals("string", cfg.getAmqpRoutingKey());
	}

	private static void validateConfig(NimrodAPI api, SetupConfig setupConfig) {
		NimrodConfig cfg = api.getConfig();
		Assertions.assertEquals(setupConfig.workDir(), cfg.getWorkDir());
		Assertions.assertEquals(setupConfig.storeDir(), cfg.getRootStore());
		Assertions.assertEquals(setupConfig.amqp().toNimrodUri(), cfg.getAmqpUri());
		Assertions.assertEquals(setupConfig.amqp().routingKey(), cfg.getAmqpRoutingKey());
		Assertions.assertEquals(setupConfig.transfer().toNimrodUri(), cfg.getTransferUri());

		Assertions.assertEquals(api.getProperties(), setupConfig.properties());

		Map<String, String> types = api.getResourceTypeInfo().stream()
				.collect(Collectors.toMap(rti -> rti.type, rti -> rti.className));
		Assertions.assertEquals(setupConfig.resourceTypes(), types);

		Map<String, AgentDefinition> agentDefs = api.lookupAgents();
		Map<String, Path> agentPlatforms = agentDefs.values().stream().collect(Collectors.toMap(
				AgentDefinition::getPlatformString,
				AgentDefinition::getPath
		));
		Assertions.assertEquals(setupConfig.agents(), agentPlatforms);

		Map<MachinePair, String> posixMappings = agentDefs.values().stream().flatMap(
				def -> def.posixMappings().stream().map(mp -> Map.entry(mp, def.getPlatformString()))
		).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		Assertions.assertEquals(setupConfig.agentMappings(), posixMappings);
	}

	@Test
	public void setupTests() {
		NimrodAPI api = getNimrod();

		SetupConfig cfg = getTestSetupConfig(getRoot());

		/* Validate the initial state. */
		validateConfig(api, cfg);

		/* This is idempotent, there should be no change. */
		NimrodUtils.setupApi(api, cfg);
		validateConfig(api, cfg);
	}

	public static SetupConfig getTestSetupConfig(Path root) {
		TestNimrodConfig nimcfg = new TestNimrodConfig(root);
		AgentProvider ap = new TestAgentProvider(root);

		SetupConfigBuilder b = new SetupConfigBuilder()
				.workDir(nimcfg.getWorkDir())
				.storeDir(nimcfg.getRootStore())
				.amqp(new AMQPConfigBuilder()
						.uri(nimcfg.getAmqpUri().uri)
						.routingKey(nimcfg.getAmqpRoutingKey())
						.certPath(nimcfg.getAmqpUri().certPath)
						.noVerifyPeer(nimcfg.getAmqpUri().noVerifyPeer)
						.noVerifyHost(nimcfg.getAmqpUri().noVerifyHost)
						.build())
				.transfer(new TransferConfigBuilder()
						.uri(nimcfg.getTransferUri().uri)
						.certPath(nimcfg.getTransferUri().certPath)
						.noVerifyPeer(nimcfg.getTransferUri().noVerifyPeer)
						.noVerifyHost(nimcfg.getTransferUri().noVerifyHost)
						.build())
				.resourceType("dummy", DummyResourceType.class)
				.property("nimrod.sched.default.launch_penalty", -10)
				.property("nimrod.sched.default.spawn_cap", 10)
				.property("nimrod.sched.default.job_buf_size", 1000)
				.property("nimrod.sched.default.job_buf_refill_threshold", 100)
				.property("nimrod.master.run_rescan_interval", 60);


		ap.lookupAgents().forEach((p, ai) -> {
			b.agent(p, ai.getPath());
			ai.posixMappings().forEach(e -> b.agentMapping(e, p));
		});

		return b.build();
	}
}
