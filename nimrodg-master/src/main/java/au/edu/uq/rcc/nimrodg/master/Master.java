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
package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentSubmit;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.events.ConfigChangeMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.master.sched.AgentScheduler;
import au.edu.uq.rcc.nimrodg.master.sched.JobScheduler;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.master.AAAAA.LaunchRequest;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Master implements MessageQueueListener, AutoCloseable {

	private static final Logger LOGGER = LogManager.getLogger(Master.class);

	private enum State {
		None(0),
		Started(1),
		Stopping(2),
		Stopped(3);

		private final int value;

		private State(int value) {
			this.value = value;
		}
	}

	private enum Mode {
		Enter,
		Run,
		Leave
	}

	@FunctionalInterface
	private interface StateProc {

		State run(State state, Mode mode);
	}

	private static class StateHandler {

		public final StateProc handler;
		public final State interruptState;

		public StateHandler(StateProc handler, State interruptState) {
			this.handler = handler;
			this.interruptState = interruptState;
		}

	}

	private final StateHandler[] stateHandlers;
	private State state;
	private State oldState;
	private final AtomicBoolean interruptFlag;

	private static class QTask {

		public final String callerName;
		public final Runnable runnable;

		public QTask(String callerName, Runnable runnable) {
			this.callerName = callerName;
			this.runnable = runnable;
		}
	}

	private static class _AgentMessage {

		public final AgentMessage msg;
		public final Instant receivedTime;
		public Instant processedTime;

		public _AgentMessage(AgentMessage msg, Instant receivedTime) {
			this.msg = msg;
			this.receivedTime = receivedTime;
		}
	}

	public class RunningJob {

		public final UUID uuid;
		public final Job job;
		public final JobAttempt att;
		public final NetworkJob networkJob;
		public final Agent agent;
		public final boolean managed;

		private RunningJob(UUID uuid, Job job, JobAttempt att, NetworkJob networkJob, Agent agent, boolean managed) {
			this.uuid = uuid;
			this.job = job;
			this.att = att;
			this.networkJob = networkJob;
			this.agent = agent;
			this.managed = managed;
		}
	}

	private final NimrodMasterAPI nimrod;
	private final Experiment experiment;
	private final Queue<NimrodMasterEvent> events;
	private final Heart heart;
	private final JobScheduler jobScheduler;
	private final AgentScheduler agentScheduler;

	private final LinkedBlockingDeque<QTask> taskQueue;
	private final LinkedBlockingDeque<QTask> taskQueuePrio;
	private final LinkedBlockingDeque<_AgentMessage> agentMessages;

	private final AAAAA aaaaa;
	private final ConcurrentHashMap<UUID, AgentInfo> allAgents;
	private final Map<UUID, CompletableFuture<LaunchRequest>> pendingAgentConnections;

	private final _AgentListener agentListener;
	private final _ActuatorOperations actuatorOps;

	private final Map<UUID, RunningJob> runningJobs;

	private AMQProcessor amqp;

	public Master(NimrodMasterAPI nimrod, Experiment experiment, JobSchedulerFactory jsf, AgentSchedulerFactory asf) {

		stateHandlers = new StateHandler[State.values().length];
		stateHandlers[State.None.value] = new StateHandler(null, State.None);
		stateHandlers[State.Started.value] = new StateHandler(this::startProc, State.Stopping);
		stateHandlers[State.Stopping.value] = new StateHandler(this::stoppingProc, State.Stopped);
		stateHandlers[State.Stopped.value] = new StateHandler(this::stoppedProc, State.None);
		this.state = State.Started;
		this.oldState = State.None;
		this.interruptFlag = new AtomicBoolean(false);

		this.nimrod = nimrod;
		this.experiment = experiment;
		this.events = new LinkedList<>();
		this.heart = new Heart(new _HeartOperations());
		this.jobScheduler = jsf.create();
		this.agentScheduler = asf.create();

		this.taskQueue = new LinkedBlockingDeque<>();
		this.taskQueuePrio = new LinkedBlockingDeque<>();
		this.agentMessages = new LinkedBlockingDeque<>();

		this.aaaaa = new _AAAAA();
		this.allAgents = new ConcurrentHashMap<>();
		this.pendingAgentConnections = new HashMap<>();

		this.agentListener = new _AgentListener();
		this.actuatorOps = new _ActuatorOperations();

		this.runningJobs = new HashMap<>();

		this.jobScheduler.setJobOperations(new _JobOperations());
		this.agentScheduler.setAgentOperations(new _AgentOperations());

		this.amqp = null;
	}

	/* This runs out-of-band with the state machine. */
	@Override
	public MessageOperation processAgentMessage(AgentMessage msg) throws IllegalStateException, IOException {
		if(!agentMessages.offer(new _AgentMessage(msg, Instant.now()))) {
			return MessageOperation.RejectAndRequeue;
		}

		return MessageOperation.Ack;
	}

	public void setAMQP(AMQProcessor amqp) {
		if(amqp == null || this.amqp != null) {
			throw new IllegalStateException();
		}

		this.amqp = amqp;
	}

	public void flagStop() {
		interruptFlag.compareAndSet(false, true);
	}

	/**
	 * Run a task sometime in the future.
	 *
	 * Tasks with priority are always executed, even in the shutdown handler.
	 *
	 * They should be used for things like agent and job state updates.
	 *
	 * @param name The name of the function submitting. This is for debug purposes only.
	 * @param r The task.
	 * @param prio Does this task have priority?
	 */
	private void runLater(String name, Runnable r, boolean prio) {
		if(prio) {
			taskQueuePrio.offer(new QTask(name, r));
		} else {
			taskQueue.offer(new QTask(name, r));
		}
	}

	private void runLater(String name, Runnable r) {
		runLater(name, r, false);
	}

	private AgentInfo getAgentInfo(UUID uuid) {
		return allAgents.getOrDefault(uuid, null);
	}

	private AgentInfo getAgentInfo(Agent agent) {
		return getAgentInfo(agent.getUUID());
	}

	public boolean tick() {
		if(state != oldState) {
			if(stateHandlers[oldState.value].handler != null) {
				try {
					stateHandlers[oldState.value].handler.run(oldState, Mode.Leave);
				} catch(RuntimeException e) {
					LOGGER.error("Caught exception during LEAVE transition:");
					LOGGER.catching(e);
					state = stateHandlers[state.value].interruptState;
				}
			}

			if(stateHandlers[state.value].handler == null) {
				return false;
			}

			try {
				stateHandlers[state.value].handler.run(state, Mode.Enter);
			} catch(RuntimeException e) {
				LOGGER.error("Caught exception during ENTER transition:");
				LOGGER.catching(e);
				state = stateHandlers[state.value].interruptState;
			}
		}

		oldState = state;

		if(interruptFlag.compareAndSet(true, false)) {
			state = stateHandlers[state.value].interruptState;
		} else {
			try {
				state = stateHandlers[state.value].handler.run(state, Mode.Run);
			} catch(RuntimeException e) {
				LOGGER.error("Caught exception during RUN:");
				LOGGER.catching(e);
				state = stateHandlers[state.value].interruptState;
			}
		}

		return true;
	}

	/*
	 * Register an agent with the master, creating its state machine et al.
	 */
	private AgentInfo registerAgent(AgentState as, Resource res, Optional<Actuator> act, boolean initial) {
		AgentInfo ai = new AgentInfo(as.getUUID(), res, act, new ReferenceAgent(as, agentListener, initial), as);

		/* ReferenceAgent will reset the UUID the initial flag is set. */
		if(initial) {
			as.setUUID(ai.uuid);
		}

		allAgents.put(ai.uuid, ai);
		return ai;
	}

	private void checkOrphanage() {
		Map<Resource, Collection<? extends AgentState>> agentMap = nimrod.getAssignedResources(experiment).stream()
				.collect(Collectors.toMap(
						r -> r,
						r -> nimrod.getResourceAgents(r)
				));

		for(Resource r : agentMap.keySet()) {
			CompletableFuture<Actuator> af = aaaaa.getOrLaunchActuator(r);
			for(AgentState as : agentMap.get(r)) {
				AgentInfo ai = registerAgent(as, r, Optional.empty(), false);
				heart.onAgentConnect(as.getUUID(), Instant.now());
				heart.resetPingTimer(as.getUUID());
				af.handle((a, t) -> {
					if(t != null) {
						LOGGER.warn("Cannot adopt agent {}, actuator failed to launch.", ai.uuid);
						return null;
					}

					if(a.adopt(as)) {
						LOGGER.info("Resource {} actuator adopted orphaned agent {}.", r.getPath(), as.getUUID());
						ai.actuator = Optional.of(a);
					} else {
						LOGGER.info("Resource {} actuator rejected orphaned agent {}.", r.getPath(), as.getUUID());
					}
					return null;
				});
			}
		}
	}

	private NetworkJob buildNetworkJob(JobAttempt att, Job job, AgentInfo ai) {
		/* FIXME: handle cert path, etc. */
		return nimrod.getAssignmentStatus(ai.resource, experiment)
				.map(u -> MsgUtils.resolveJob(att.getUUID(), job, Task.Name.Main, u.uri, nimrod.getJobAttemptToken(att)))
				.orElseThrow(() -> new IllegalStateException("Resource not assigned"));
	}

	private void resyncJobAttempts() {
		/*
		 * NB: This should be idempotent if possible.
		 * Get all active attempts we don't already know about.
		 */
		Map<? extends Job, Collection<? extends JobAttempt>> activeAttempts = nimrod.filterJobAttempts(experiment,
				EnumSet.of(JobAttempt.Status.NOT_RUN, JobAttempt.Status.RUNNING)
		);

		activeAttempts.entrySet().forEach(je -> je.getValue().forEach(att -> {
			if(runningJobs.containsKey(att.getUUID())) {
				return;
			}

			AgentInfo ai = allAgents.get(att.getAgentUUID());
			/* If there's no agent associated, something screwy's going on. Fail the attempt and let it reschedule. */
			if(ai == null) {
				/* FIXME: What if the job scheduler actually knows about it and somethings really gone screwy? */
				LOGGER.warn("Active job attempt {} has invalid agent {}, marking as failed.", att.getUUID(), att.getAgentUUID());
				nimrod.finishJobAttempt(att, true);
				return;
			}

			Job job = je.getKey();
			RunningJob rj = new RunningJob(att.getUUID(), job, att, buildNetworkJob(att, job, ai), ai.instance, true);
			runningJobs.put(rj.uuid, rj);
		}));

		/* Resync the schedulers. */
		runningJobs.values().stream().forEach(j -> jobScheduler.recordAttempt(j.att, j.job));
		agentScheduler.resync(
				allAgents.values().stream().map(ai -> ai.instance).collect(Collectors.toSet()),
				runningJobs.values().stream().collect(Collectors.toSet())
		);
	}

	private State startProc(State state, Mode mode) {
		if(mode == Mode.Enter) {
			/* Create psuedo-events for initial configuration values. */
			nimrod.getProperties().entrySet().stream()
					.map(e -> new ConfigChangeMasterEvent(e.getKey(), null, e.getValue()))
					.forEach(events::add);

			checkOrphanage();
			resyncJobAttempts();
			return state;
		} else if(mode == Mode.Leave) {
			return state;
		}

		/* Process database events. */
		events.addAll(nimrod.pollMasterEvents());
		events.forEach(this::processEvent);
		events.clear();

		/* Process agents. */
		processAgents(state);

		/* Process tasks. */
		processQueue(taskQueuePrio);
		processQueue(taskQueue);

		/* Tick the job scheduler. */
		if(!jobScheduler.tick()) {
			return State.Stopping;
		}

		/* Tick the agent scheduler. */
		if(!agentScheduler.tick()) {
			return State.Stopping;
		}
		return state;
	}

	private void processEvent(NimrodMasterEvent _evt) {
		switch(_evt.getType()) {
			case ConfigChange: {
				ConfigChangeMasterEvent evt = (ConfigChangeMasterEvent)_evt;

				if(evt.oldValue == null) {
					LOGGER.info("Configuration '{}' initialised to '{}'", evt.key, evt.newValue);
				} else {
					LOGGER.info("Configuration '{}' changed from '{}' -> '{}'", evt.key, evt.oldValue, evt.newValue);
				}

				jobScheduler.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				agentScheduler.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				heart.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				break;
			}

			case JobAdd: {
				jobScheduler.onJobAdd(((JobAddMasterEvent)_evt).job);
				break;
			}
		}
	}

	private void processAgents(State state) {
		List<_AgentMessage> msgs = new ArrayList<>();
		agentMessages.drainTo(msgs);

		for(_AgentMessage msg : msgs) {
			MessageOperation mop;
			try {
				mop = this.doProcessAgentMessage2(state, msg);
			} catch(IOException | IllegalStateException e) {
				LOGGER.error("Caught exception processing agent message ");
				LOGGER.catching(e);
				continue;
			}

			/* Requeue the message if the handler bounced it */
			if(mop == MessageOperation.RejectAndRequeue) {
				agentMessages.offer(msg);
			}
		}

		heart.tick(Instant.now());
	}

	private void processQueue(BlockingDeque<QTask> tasks) {
		/* Use a secondary queue so we don't get stuck in an infinite runSync() loop */
		List<QTask> _tasks = new ArrayList<>();
		tasks.drainTo(_tasks);
		_tasks.forEach(qt -> qt.runnable.run());
	}

	private MessageOperation doProcessAgentMessage2(State state, _AgentMessage _msg) throws IllegalStateException, IOException {
		_msg.processedTime = Instant.now();
		AgentMessage msg = _msg.msg;
		LOGGER.debug("doProcessAgentMessage({}, {})", msg.getAgentUUID(), msg.getTypeString());

		AgentInfo ai = getAgentInfo(msg.getAgentUUID());
		if(ai == null) {
			if(msg.getType() != AgentMessage.Type.Hello) {
				LOGGER.warn("Message from unknown agent {}, terminating...", msg.getAgentUUID());
				return MessageQueueListener.MessageOperation.Terminate;
			}

			AgentHello hello = (AgentHello)msg;
			LOGGER.trace("Received agent.hello with (uuid, queue) = ({}, {})", hello.getAgentUUID(), hello.queue);

			if(state != State.Started) {
				LOGGER.warn("Agent connection during shutdown, terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}

			CompletableFuture<LaunchRequest> _info;
			if((_info = pendingAgentConnections.get(hello.getAgentUUID())) == null) {
				LOGGER.warn("Agent connection unexpected, terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}

			/*
			 * Handle cases where the agent's connected before the launch has technically finished. 
			 * This happens sometimes with PBS, the jobs start before qsub's returned.
			 */
			assert !_info.isDone();
			LOGGER.debug("Agent connected, but launch hasn't finished, deferring...");
			return MessageQueueListener.MessageOperation.RejectAndRequeue;
		}

		assert ai != null;

		// TODO: Consider doing an adoption check here
		// TODO: Handle expiry (by checking nimrod)

		/* If we're an agent.hello, validate and register it. */
		if(msg.getType() == AgentMessage.Type.Hello) {
			AgentHello hello = (AgentHello)msg;
			LOGGER.trace("Received agent.hello with (uuid, queue) = ({}, {})", hello.getAgentUUID(), hello.queue);

			String queue = ai.state.getQueue();
			if(queue != null) {
				if(hello.queue.equals(queue)) {
					/* Agent sent a superflous hello, we can just ignore this. */
					return MessageOperation.Ack;
				} else {
					/* Okay, there's actually a duplicate UUID. Dafuq? */
					LOGGER.warn("Agent connection with duplicate UUID, buy a lottery ticket.");
					return MessageQueueListener.MessageOperation.Terminate;
				}
			}

			if(state != State.Started) {
				LOGGER.warn("Agent connection during shutdown, terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}

			/* If we're this far, this future should be complete. */
			LaunchRequest lrq = pendingAgentConnections.get(hello.getAgentUUID()).getNow(null);
			assert lrq != null && !lrq.launchResults.isCompletedExceptionally() && lrq.launchResults.isDone();
			Actuator.LaunchResult[] launchResults = lrq.launchResults.getNow(null);

			int batchIndex = -1;
			{
				for(int i = 0; i < lrq.uuids.length; ++i) {
					if(lrq.uuids[i] == null) {
						continue;
					}

					if(lrq.uuids[i].equals(ai.uuid)) {
						batchIndex = i;
						break;
					}
				}
			}

			if(batchIndex < 0) {
				throw new IllegalStateException("batchIndex < 0, this should never happen");
			}
			ai.state.setExpiryTime(launchResults[batchIndex].expiryTime);
		} else if(msg.getType() == AgentMessage.Type.Shutdown && ai.state.getState() == Agent.State.SHUTDOWN) {
			/*
			 * We can ignore a stray shutdown.
			 *
			 * This might also happen when a local agent dies and the actuator can't be sure
			 * it shut down properly.
			 */
			return MessageQueueListener.MessageOperation.Terminate;
		}

		/* Process the agent message, do this in the main thread. */
		Agent _ag = ai.instance;
		try {
			_ag.processMessage(msg, _msg.processedTime);
		} catch(IOException | IllegalStateException | IllegalArgumentException e) {
			try {
				_ag.terminate();
			} catch(IOException ex) {
				/* nop */
			}
			throw e;
		}

		return MessageQueueListener.MessageOperation.Ack;
	}

	private State stoppingProc(State state, Mode mode) {
		if(mode == Mode.Enter) {
			aaaaa.shutdown();
			for(AgentInfo ai : allAgents.values()) {
				try {
					LOGGER.trace("Terminating agent '{}'", ai.uuid);
					ai.instance.terminate();
				} catch(IOException e) {
					LOGGER.warn("Unable to kill agent '{}', mimicking successful shutdown.", ai.uuid);
					LOGGER.catching(e);
					ai.instance.disconnect(AgentShutdown.Reason.Requested, -1);
				}
			}

			return State.Stopping;
		} else if(mode == Mode.Leave) {
			return State.Stopping;
		}

		/* Process agents. */
		processAgents(state);

		/* Process priority tasks. */
		processQueue(taskQueuePrio);

		if(!aaaaa.isShutdown() || !allAgents.isEmpty() || !taskQueuePrio.isEmpty()) {
			return State.Stopping;
		}

		return State.Stopped;
	}

	private State stoppedProc(State state, Mode mode) {
		return State.None;
	}

	@Override
	public void close() {
		aaaaa.close();
	}

	private class _JobOperations implements JobScheduler.Operations {

		@Override
		public Experiment getExperiment() {
			return experiment;
		}

		@Override
		public JobAttempt runJob(Job j) {
			JobAttempt att = nimrod.createJobAttempt(j);
			agentScheduler.onJobRun(att);
			return att;
		}

		@Override
		public void cancelJob(JobAttempt att) {
			agentScheduler.onJobCancel(att);
		}

		@Override
		public void updateExperimentState(Experiment.State state) {
			nimrod.updateExperimentState(experiment, state);
		}

		@Override
		public void updateJobStarted(JobAttempt att, UUID agentUuid) {
			nimrod.startJobAttempt(att, agentUuid);
		}

		@Override
		public void updateJobFinished(JobAttempt att, boolean failed) {
			nimrod.finishJobAttempt(att, failed);
		}

		@Override
		public void recordCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) {
			nimrod.addCommandResult(att, status, index, time, retval, message, errcode, stop);
		}

	}

	private class _AgentOperations implements AgentScheduler.Operations {

		@Override
		public UUID[] launchAgents(Resource res, int num) {
			if(!nimrod.isResourceAssigned(res, experiment)) {
				throw new IllegalArgumentException();
			}

			UUID[] uuids = AAAAA.generateRandomUUIDs(num);
			CompletableFuture<LaunchRequest> fff = new CompletableFuture<>();
			for(int i = 0; i < num; ++i) {
				pendingAgentConnections.put(uuids[i], fff);
			}

			LaunchRequest rq = aaaaa.launchAgents(res, uuids);

			/*
			 * NB: rq.launchResults will never fail. In the case of an actuator failure,
			 * AAAAA will fail each request instead of the entire future.
			 */
			rq.launchResults.thenAccept(lrs -> {
				for(int i = 0; i < lrs.length; ++i) {
					Optional<Actuator> act;
					if(rq.actuatorFuture.isCompletedExceptionally()) {
						act = Optional.empty();
					} else {
						act = Optional.ofNullable(rq.actuatorFuture.getNow(null));
					}

					Actuator.LaunchResult lr = lrs[i];
					UUID uuid = rq.uuids[i];
					runLater("launchAgents", () -> {
						if(lr.t != null) {
							agentScheduler.onAgentLaunchFailure(uuid, res, lr.t);
							pendingAgentConnections.remove(uuid);
						} else {
							AgentState as = new DefaultAgentState();
							as.setUUID(uuid);
							as.setActuatorData(lr.actuatorData);
							AgentInfo ai = registerAgent(as, res, act, true);
							nimrod.addAgent(res, ai.state);
						}

						fff.complete(rq);
					}, true);
				}
			});

			return rq.uuids;
		}

		@Override
		public void terminateAgent(Agent agent) {
			runLater("agentDoTerminateAgent", () -> {
				LOGGER.trace("Termination of agent '{}' requested.", agent.getUUID());
				try {
					agent.terminate();
				} catch(IOException e) {
					LOGGER.trace("Termination failed.");
					LOGGER.catching(e);
				}
			});
		}

		@Override
		public UUID runJob(JobAttempt att, Agent agent) {
			Job job = att.getJob();
			LOGGER.info("Run job '{}' on agent '{}'", job.getPath(), agent.getUUID());

			UUID uuid = att.getUUID();

			NetworkJob nj = buildNetworkJob(att, job, allAgents.get(agent.getUUID()));

			Throwable t = null;
			try {
				agent.submitJob(nj);
			} catch(IOException | IllegalArgumentException | IllegalStateException e) {
				t = e;
				LOGGER.catching(e);
			}

			if(t == null) {
				runningJobs.put(uuid, new RunningJob(uuid, job, att, nj, agent, true));
				runLater("agentDoRunJob(js:onJobLaunchSuccess)", () -> jobScheduler.onJobLaunchSuccess(att, agent.getUUID()), true);
			} else {
				/* This is an agent issue, try reschedule the job. */
				Throwable _t = t;
				runLater("agentDoRunJob(as:onJobLaunchFailure)", () -> agentScheduler.onJobLaunchFailure(att, nj, agent, false, _t), true);
			}

			return uuid;
		}

		@Override
		public void runUnmanagedJob(NetworkJob job, Agent agent) throws IllegalArgumentException {
			runLater("agentDoRunUnmanagedJob", () -> {
				if(runningJobs.containsKey(job.uuid)) {
					throw new IllegalArgumentException();
				}

				try {
					agent.submitJob(job);
				} catch(IOException | IllegalArgumentException | IllegalStateException e) {
					runLater("agentDoRunUnmanagedJob", () -> agentScheduler.onJobLaunchFailure(null, job, agent, true, e));
					return;
				}

				runningJobs.put(job.uuid, new RunningJob(job.uuid, null, null, job, agent, false));
			});
		}

		@Override
		public void cancelCurrentJob(Agent agent) {
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public Collection<Resource> getResources() {
			return nimrod.getResources().stream().map(n -> (Resource)n).collect(Collectors.toList());
		}

		@Override
		public Collection<Resource> getAssignedResources(Experiment exp) {
			return nimrod.getAssignedResources(exp).stream().map(r -> (Resource)r).collect(Collectors.toList());
		}

		@Override
		public Optional<NimrodURI> resolveTransferUri(Resource res, Experiment exp) {
			return nimrod.getAssignmentStatus(res, exp);
		}

		@Override
		public boolean isResourceCapable(Resource node, Experiment exp) {
			return nimrod.isResourceCapable(node, exp);
		}

		@Override
		public void addResourceCaps(Resource node, Experiment exp) {
			nimrod.addResourceCaps(node, exp);
		}

		@Override
		public Resource getAgentResource(Agent agent) {
			return allAgents.get(agent.getUUID()).resource;
		}

		@Override
		public List<Agent> getResourceAgents(Resource node) {
			return allAgents.values().stream()
					.filter(ai -> ai.resource.equals(node))
					.map(ai -> ai.instance)
					.collect(Collectors.toList());
		}

		@Override
		public void reportJobFailure(JobAttempt att, Agent agent, FailureReason reason) {
			runLater("agentReportJobFailure", () -> jobScheduler.onJobFailure(att, reason));
		}
	}

	private class _AgentListener implements ReferenceAgent.AgentListener {

		@Override
		public void send(Agent agent, AgentMessage msg) throws IOException {
			amqp.sendMessage(agent.getQueue(), msg);
		}

		@Override
		public void onStateChange(Agent agent, Agent.State oldState, Agent.State newState) {
			if(oldState == null) {
				/* Initial state setup, we don't do anything here. */
				return;
			}

			LOGGER.debug("Agent {}: State change from {} -> {}", agent.getUUID(), oldState, newState);

			AgentInfo ai = getAgentInfo(agent);
			assert agent == ai.instance;

			if(oldState == Agent.State.WAITING_FOR_HELLO && newState == Agent.State.READY) {
				ai.state.setConnectionTime(Instant.now());
				ai.actuator.ifPresent(a -> a.notifyAgentConnection(ai.state));
				heart.onAgentConnect(ai.uuid, Instant.now());
			} else {
				if(newState == Agent.State.SHUTDOWN) {
					ai.state.setExpired(true);
				}

				if(newState == Agent.State.SHUTDOWN) {
					ai.actuator.ifPresent(a -> a.notifyAgentDisconnection(ai.uuid));
					heart.onAgentDisconnect(ai.uuid);
					allAgents.remove(ai.uuid);
				}
			}
			nimrod.updateAgent(ai.state);

			/* Execute this with priority so it's processed before the next scheduler tick. */
			runLater("agOnStateChange", () -> agentScheduler.onAgentStateUpdate(agent, ai.resource, oldState, newState), true);
		}

		@Override
		public void onJobSubmit(Agent agent, AgentSubmit as) {
			/* nop */
		}

		@Override
		public void onJobUpdate(Agent agent, AgentUpdate au) {
			UUID uuid = au.getJobUUID();

			RunningJob rj = runningJobs.get(uuid);

			assert rj.agent.equals(agent);
			if(rj.managed) {
				/* Managed job, it's for the job scheduler */
				runLater("agOnJobUpdate", () -> jobScheduler.onJobUpdate(rj.att, au, rj.networkJob.numCommands), true);
			} else {
				/* Unmanaged job, it's for the agent scheduler */
				runLater("agOnJobUpdate", () -> agentScheduler.onUnmanagedJobUpdate(rj.networkJob, au, agent), true);
			}
		}

		@Override
		public void onPong(Agent agent, AgentPong pong) {
			heart.onAgentPong(agent.getUUID());
			nimrod.updateAgent(((ReferenceAgent)agent).getDataStore());
		}
	}

	private class _AAAAA extends AAAAA {

		@Override
		protected Actuator createActuator(Resource resource) throws IOException, IllegalArgumentException {
			NimrodURI amqpUri = resource.getAMQPUri();
			Certificate[] certs;
			try {
				certs = ActuatorUtils.readX509Certificates(amqpUri.certPath);
			} catch(CertificateException e) {
				throw new IOException(e);
			}

			return nimrod.createActuator(actuatorOps, resource, certs);
		}
	}

	private class _ActuatorOperations implements Actuator.Operations {

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {
			agentMessages.offer(new _AgentMessage(new AgentShutdown(uuid, reason, signal), Instant.now()));
		}

		@Override
		public NimrodMasterAPI getNimrod() {
			return nimrod;
		}
	}

	private class _HeartOperations implements Heart.Operations {

		@Override
		public void expireAgent(UUID u) {
			AgentInfo ai = allAgents.remove(u);
			ai.state.setExpired(true);
			ai.actuator.ifPresent(a -> {
				a.forceTerminateAgent(u);
				a.notifyAgentDisconnection(u);
			});
			runLater("heartExpireAgent", () -> agentScheduler.onAgentExpiry(u));
			nimrod.updateAgent(ai.state);
		}

		@Override
		public void terminateAgent(UUID u) {
			try {
				allAgents.get(u).instance.terminate();
			} catch(IOException e) {
				LOGGER.catching(e);
			}
		}

		@Override
		public void disconnectAgent(UUID u, AgentShutdown.Reason reason, int signal) {
			allAgents.get(u).instance.disconnect(reason, signal);
		}

		@Override
		public void pingAgent(UUID u) {
			try {
				allAgents.get(u).instance.ping();
			} catch(IOException e) {
				LOGGER.catching(e);
			}
		}

		@Override
		public Instant getLastHeardFrom(UUID u) {
			return allAgents.get(u).state.getLastHeardFrom();
		}

		@Override
		public Instant getExpiryTime(UUID u) {
			return allAgents.get(u).state.getExpiryTime();
		}

	}
}
