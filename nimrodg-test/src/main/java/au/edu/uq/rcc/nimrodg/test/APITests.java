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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.NimrodEntity;
import au.edu.uq.rcc.nimrodg.api.NimrodServeAPI;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import java.io.IOException;
import java.net.URI;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.json.JsonValue;
import org.junit.Test;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Optional;
import javax.json.Json;
import org.junit.Assert;

public abstract class APITests {

	public static ResourceTypeInfo DUMMY_RESOURCE_INFO = new ResourceTypeInfo("dummy", DummyResourceType.class.getCanonicalName(), DummyResourceType.class);

	protected abstract NimrodAPI getNimrod();

	@Test
	public void getNonExistentExperimentTestw() {
		Assert.assertNull(getNimrod().getExperiment("asdfasdf"));
	}

	@Test
	public void experimentEnumerationTest() throws NimrodAPIException, IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("test1", TestUtils.getSampleExperiment());
		Experiment exp2 = api.addExperiment("test2", TestUtils.getSampleExperiment());
		Experiment exp3 = api.addExperiment("test3", TestUtils.getSampleExperiment());
		Experiment exp4 = api.addExperiment("test4", TestUtils.getSampleExperiment());

		Experiment[] exp = api.getExperiments().stream().toArray(Experiment[]::new);
		Assert.assertArrayEquals(new Experiment[]{ exp1, exp2, exp3, exp4 }, exp);
	}

	@Test
	public void basicTests() throws NimrodAPIException, IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Experiment exp2 = api.getExperiment("test1");

		Assert.assertEquals(exp, exp2);

		Collection<? extends Task> tasks = exp.getTasks();

		Job newJob;
		{
			HashMap<String, String> vals = new HashMap<>();
			vals.put("x", "xxx");
			vals.put("y", "yyy");
			newJob = api.addSingleJob(exp, vals);

			vals.put("jobindex", "3");
			vals.put("jobname", "3");
			Assert.assertEquals(vals, newJob.getVariables());
		}

		List<? extends Job> jobs = new ArrayList<>(exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 0));

		Assert.assertEquals(newJob, jobs.get(2));

		Resource node = api.addResource("test", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		api.assignResource(node, exp);

		List<Resource> ass = new ArrayList<>(api.getAssignedResources(exp));

		Assert.assertEquals(node, ass.get(0));
	}

	@Test
	public void runNotReturningImplicitVariablesTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Collection<String> vars = exp.getVariables();

		/* Make sure we don't contain any of our implicit variables. */
		Assert.assertEquals(2, vars.size());
		Assert.assertTrue(vars.contains("x"));
		Assert.assertTrue(vars.contains("y"));

	}

	@Test
	public void jobAttemptTests() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSampleExperiment());

		Assert.assertTrue(api.getAPICaps().master);
		NimrodMasterAPI mapi = (NimrodMasterAPI)api;

		Job j = exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream().findFirst().get();

		/* Create one job a attempt and make it run successfully. */
		{
			JobAttempt att = mapi.createJobAttempt(j);

			Assert.assertNull(att.getAgentUUID());
			Assert.assertNull(att.getStartTime());
			Assert.assertNull(att.getFinishTime());
			Assert.assertEquals(JobAttempt.Status.NOT_RUN, att.getStatus());
			Assert.assertEquals(JobAttempt.Status.NOT_RUN, j.getStatus());

			UUID agentUuid = UUID.randomUUID();
			mapi.startJobAttempt(att, agentUuid);

			Assert.assertEquals(agentUuid, att.getAgentUUID());
			Assert.assertNotNull(att.getStartTime());
			Assert.assertNull(att.getFinishTime());
			Assert.assertEquals(JobAttempt.Status.RUNNING, att.getStatus());
			Assert.assertEquals(JobAttempt.Status.RUNNING, j.getStatus());

			/* Add a command result for goot measure. Note this has no affect on the attempt status. */
			mapi.addCommandResult(att, CommandResult.CommandResultStatus.SUCCESS, 1, 10.0f, 0, "Success", 0, true);

			mapi.finishJobAttempt(att, false);
			Assert.assertEquals(agentUuid, att.getAgentUUID());
			Assert.assertNotNull(att.getStartTime());
			Assert.assertNotNull(att.getFinishTime());
			Assert.assertEquals(JobAttempt.Status.COMPLETED, att.getStatus());
			Assert.assertEquals(JobAttempt.Status.COMPLETED, j.getStatus());
		}

		/* Create another attempt and fail it immediately */
		{
			JobAttempt att = mapi.createJobAttempt(j);

			Assert.assertNull(att.getAgentUUID());
			Assert.assertNull(att.getStartTime());
			Assert.assertNull(att.getFinishTime());
			Assert.assertEquals(JobAttempt.Status.NOT_RUN, att.getStatus());
			Assert.assertEquals(JobAttempt.Status.COMPLETED, j.getStatus());

			mapi.finishJobAttempt(att, true);

			Assert.assertNull(att.getAgentUUID());
			Assert.assertNotNull(att.getStartTime());
			Assert.assertNotNull(att.getFinishTime());
			Assert.assertEquals(JobAttempt.Status.FAILED, att.getStatus());
			Assert.assertEquals(JobAttempt.Status.COMPLETED, j.getStatus());
		}
	}

	@Test
	public void substitutionApplicationTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp = api.addExperiment("test1", TestUtils.getSimpleSampleExperiment());

		Job job = exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream().findFirst().get();

		NetworkJob nj = MsgUtils.resolveJob(UUID.randomUUID(), job, Task.Name.Main, URI.create("http://localhost"));

		NetworkJob.ExecCommand cmd = (NetworkJob.ExecCommand)nj.commands.get(0);

		Assert.assertEquals("echo value-x-0 value-y-0", cmd.arguments.get(0));
	}

	@Test
	public void benchSubstitutionTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		CompiledRun cr = TestUtils.getBenchRun();

		Experiment exp = api.addExperiment("testbench1", cr);

		Job job = exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 1).stream().findFirst().get();

		NetworkJob nj = MsgUtils.resolveJob(UUID.randomUUID(), job, Task.Name.Main, URI.create("http://localhost"));

		NetworkJob.ExecCommand cmd = (NetworkJob.ExecCommand)nj.commands.get(1);
		Assert.assertEquals("/home/uqzvanim/nimbench.sh GET 1kb", cmd.arguments.get(0));
		int x = 0;
	}

	private class _FactuatorOps implements Actuator.Operations {

		public final NimrodMasterAPI nimrod;

		public _FactuatorOps(NimrodMasterAPI nimrod) {
			this.nimrod = nimrod;
		}

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {

		}

		@Override
		public NimrodMasterAPI getNimrod() {
			return nimrod;
		}
	}

	@Test
	public void fakeAgentTests() throws IllegalArgumentException, IOException {
		NimrodAPI api = getNimrod();
		//onPaperAssignmentTest();
		Assert.assertTrue(api.getAPICaps().master);
		NimrodMasterAPI napi = (NimrodMasterAPI)api;

		Resource rootResource = api.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		Map<UUID, Resource> nodeMap = new HashMap<>();
		/*
		 * Get the actuator to "launch" agents.
		 * It really just creates a bunch of agent.hello messages, which is the same thing, really.
		 */
		List<AgentHello> hellos;
		try( Actuator act = napi.createActuator(new _FactuatorOps(napi), rootResource, NimrodURI.create(URI.create("amqp://dummy-server/vhost"), "/not/a/path", true, true), new Certificate[]{})) {
			UUID[] uuids = new UUID[10];
			for(int i = 0; i < uuids.length; ++i) {
				uuids[i] = UUID.randomUUID();
			}

			Actuator.LaunchResult[] lrs = act.launchAgents(uuids);
			for(int i = 0; i < uuids.length; ++i) {
				nodeMap.put(uuids[i], lrs[i].node);
			}

			hellos = ((DummyActuator)act).simulateHellos();

			ReferenceAgent[] agents = new ReferenceAgent[hellos.size()];

			FakeAgentListener l = new FakeAgentListener(napi, (DummyActuator)act);

			for(int i = 0; i < agents.length; ++i) {
				agents[i] = new ReferenceAgent(new DefaultAgentState(), l);
				agents[i].processMessage(hellos.get(i), Instant.now());

				Assert.assertEquals(
						Json.createObjectBuilder()
								.add("hashCode", agents[i].getDataStore().hashCode())
								.build(),
						agents[i].getDataStore().getActuatorData()
				);
			}
		}
	}

	@Test
	public void complexResourceAssignmentTest() throws NimrodAPIException, IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
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

		Collection<? extends Resource> roots = api.getResources();
		Assert.assertEquals(5, roots.size());
		Assert.assertTrue(roots.contains(root));
		Assert.assertTrue(roots.contains(tinaroo));
		Assert.assertTrue(roots.contains(awoonga));
		Assert.assertTrue(roots.contains(flashlite));
		Assert.assertTrue(roots.contains(nectar));

		{
			Collection<? extends Resource> res = api.getAssignedResources(exp1);
			Assert.assertTrue(res.contains(root));
			Assert.assertTrue(res.contains(flashlite));
		}

		{
			Collection<? extends Resource> res = api.getAssignedResources(exp2);
			Assert.assertTrue(res.contains(root));
		}

		{
			Collection<? extends Resource> res = api.getAssignedResources(exp3);
			Assert.assertTrue(res.contains(root));
		}
	}

	@Test
	public void testCapabilityTest() throws NimrodAPIException, IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		TestUtils.createSampleResources(api);

		Assert.assertTrue(api.getAPICaps().master);
		NimrodMasterAPI napi = (NimrodMasterAPI)api;

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSampleExperiment());

		Experiment exp2 = api.addExperiment("exp2", TestUtils.getSampleExperiment());

		/* Assign tinaroo to exp1 */
		Resource tinaroo = api.getResource("tinaroo");
		napi.assignResource(tinaroo, exp1);

		/* Everything should be incapable initially. */
		Assert.assertFalse(napi.isResourceCapable(tinaroo, exp1));
		Assert.assertFalse(napi.isResourceCapable(tinaroo, exp2));

		/* Make tinaroo capable of exp1. */
		napi.addResourceCaps(tinaroo, exp1);
		Assert.assertTrue(napi.isResourceCapable(tinaroo, exp1));

		/* tinaroo isn't assigned to exp2, so this shouldn't do anything */
		napi.addResourceCaps(tinaroo, exp2);
		Assert.assertTrue(napi.isResourceCapable(tinaroo, exp2));

		/* Unassign tinaroo from exp1 and check its capability was removed */
		napi.unassignResource(tinaroo, exp1);
		Assert.assertFalse(napi.isResourceCapable(tinaroo, exp1));
	}

	@Test
	public void masterJobMessageTests() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Assert.assertTrue(api.getAPICaps().master);
		NimrodMasterAPI mapi = (NimrodMasterAPI)api;
		Experiment exp = mapi.addExperiment("test1", TestUtils.getSampleExperiment());

		/* Drain any initial config change events. */
		mapi.pollMasterEvents();

		/* A job added to a stopped run shouldn't generate an event. */
		Assert.assertEquals(Experiment.State.STOPPED, exp.getState());
		mapi.addSingleJob(exp, new HashMap<String, String>() {
			{
				put("x", "xx");
				put("y", "yy");
			}
		});

		Collection<NimrodMasterEvent> _evts = mapi.pollMasterEvents();
		Assert.assertEquals(0, _evts.size());

		/* Start the experiment, this should cause an event to be created. */
		mapi.updateExperimentState(exp, Experiment.State.STARTED);
		mapi.addSingleJob(exp, new HashMap<String, String>() {
			{
				put("x", "xx");
				put("y", "yy");
			}
		});

		List<NimrodMasterEvent> evts = new ArrayList<>(mapi.pollMasterEvents());
		Assert.assertEquals(1, evts.size());

		{
			NimrodMasterEvent nme = evts.get(0);
			Assert.assertEquals(NimrodMasterEvent.Type.JobAdd, nme.getType());

			JobAddMasterEvent ja = (JobAddMasterEvent)nme;
			Assert.assertEquals(exp, ja.exp);
		}
	}

	@Test
	public void multipleExperimentsTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSampleExperiment());
		Experiment exp2 = api.addExperiment("exp2", TestUtils.getSampleExperiment());
	}

	@Test
	public void commandNormalisationTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSampleExperiment());

		List<Command> allCommands = exp1.getTasks().stream()
				.flatMap(t -> t.getCommands().stream())
				.collect(Collectors.toList());
	}

	@Test
	public void multiJobTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());

		Assert.assertEquals(Set.of("x", "y"), exp1.getVariables());
		Assert.assertTrue(exp1.filterJobs(null, 0, 0).isEmpty());
	}

	@Test
	public void tokenTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleExperiment());
		String token = exp1.getToken();

		NimrodServeAPI sapi = (NimrodServeAPI)api;
		NimrodEntity expEnt = sapi.isTokenValidForStorage(exp1, token);
		Assert.assertEquals(exp1, expEnt);
		Assert.assertNull(sapi.isTokenValidForStorage(exp1, "asdf"));

		NimrodMasterAPI mapi = (NimrodMasterAPI)api;

		Job j = exp1.filterJobs(EnumSet.of(JobAttempt.Status.NOT_RUN), 0, 1).stream().findFirst().get();

		JobAttempt att = mapi.createJobAttempt(j);
		String attToken = mapi.getJobAttemptToken(att);

		NimrodEntity attEnt = sapi.isTokenValidForStorage(exp1, attToken);
		Assert.assertEquals(att, attEnt);
		Assert.assertNull(sapi.isTokenValidForStorage(exp1, "asdf"));
	}

	@Test
	public void agentLookupTests() {
		NimrodAPI api = getNimrod();

		AgentInfo ai = api.lookupAgentByPlatform("x86_64-pc-linux-musl");
		Assert.assertNotNull(ai);
		Assert.assertEquals("x86_64-pc-linux-musl", ai.getPlatformString());

		Set<Map.Entry<String, String>> x64mappings = new HashSet<>();
		x64mappings.add(new AbstractMap.SimpleImmutableEntry<>("Linux", "x86_64"));
		x64mappings.add(new AbstractMap.SimpleImmutableEntry<>("Linux", "k10m"));
		Assert.assertEquals(x64mappings, new HashSet<>(ai.posixMappings()));

		AgentInfo ai2 = api.lookupAgentByPosix("Linux", "x86_64");
		Assert.assertNotNull(ai2);
		Assert.assertEquals(ai, ai2);

		ai2 = api.lookupAgentByPosix("Linux", "k10m");
		Assert.assertNotNull(ai2);
		Assert.assertEquals(ai, ai2);
	}

	@Test
	public void assignmentStateTests() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();

		Experiment exp1 = api.addExperiment("exp1", TestUtils.getSimpleSampleExperiment());

		Resource res = api.addResource("test1", "dummy", JsonValue.EMPTY_JSON_OBJECT, null,
				NimrodURI.create(URI.create("file:///path/to/storage/root/"), null, null, null)
		);

		/* Test a with a custom experiment mapping. */
		{
			api.assignResource(res, exp1, Optional.ofNullable(NimrodURI.create(URI.create("file:///some/other/path/to/root/"), null, null, null)));

			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assert.assertTrue(nuri.isPresent());

			NimrodURI txUri = nuri.get();
			Assert.assertEquals(URI.create("file:///some/other/path/to/root/"), txUri.uri.normalize());

			api.unassignResource(res, exp1);
		}

		/* Test with no custom mapping. */
		{
			api.assignResource(res, exp1);
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assert.assertTrue(nuri.isPresent());

			URI expUri = res.getTransferUri().uri.resolve(exp1.getWorkingDirectory());
			NimrodURI txUri = nuri.get();
			Assert.assertEquals(expUri, txUri.uri.normalize());

			api.unassignResource(res, exp1);
		}

		/* Test no assignment, with resource override. */
		{
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res, exp1);
			Assert.assertFalse(nuri.isPresent());
		}

		Resource res2 = api.addResource("test2", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);

		/* Test assignment, with global fallback. */
		{
			api.assignResource(res2, exp1);
			Optional<NimrodURI> nuri = api.getAssignmentStatus(res2, exp1);
			Assert.assertTrue(nuri.isPresent());

			URI expUri = api.getConfig().getTransferUri().uri.resolve(exp1.getWorkingDirectory());
			NimrodURI txUri = nuri.get();
			Assert.assertEquals(expUri, txUri.uri.normalize());

			api.unassignResource(res2, exp1);
		}
	}

	@Test
	public void jobAdditionTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();

		Experiment exp = api.addExperiment("exp1", TestUtils.getSimpleSampleEmptyExperiment());

		List<? extends Job> jobs = new ArrayList<>(exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assert.assertEquals(0, jobs.size());

		List<Map<String, String>> newJobs = new ArrayList<>();
		api.addJobs(exp, newJobs);

		jobs = new ArrayList<>(exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assert.assertEquals(0, jobs.size());

		newJobs.add(new HashMap<>(){{
			put("x", "0");
			put("y", "1");
		}});
		api.addJobs(exp, newJobs);

		jobs = new ArrayList<>(exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, 0));
		Assert.assertEquals(1, jobs.size());
	}

	@Test
	public void add250000JobsTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		NimrodAPI api = getNimrod();
		/* This will trigger postgres to batch jobs. */
		Experiment exp = api.addExperiment("exp1", TestUtils.get250000Run());
	}
}
