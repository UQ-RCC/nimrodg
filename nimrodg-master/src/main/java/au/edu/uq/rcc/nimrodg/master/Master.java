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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentPong;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.Actuator.LaunchResult;
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
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.master.AAAAA.LaunchRequest;
import au.edu.uq.rcc.nimrodg.api.ActuatorOpsAdapter;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.hpc.HPCActuator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Master implements MessageQueueListener, AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(Master.class);

	private enum State {
		None(0),
		Started(1),
		Stopping(2),
		Stopped(3);

		private final int value;

		State(int value) {
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

		public final long tag;
		public final AgentMessage msg;
		public final Instant receivedTime;
		public Instant processedTime;

		public _AgentMessage(long tag, AgentMessage msg, Instant receivedTime) {
			this.tag = tag;
			this.msg = msg;
			this.receivedTime = receivedTime;
		}
	}

	public static class RunningJob {

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
	private final _HeartOperations heartOps;
	private final Heart heart;
	private final JobScheduler jobScheduler;
	private final AgentScheduler agentScheduler;

	private final LinkedBlockingDeque<QTask> taskQueue;
	private final LinkedBlockingDeque<QTask> taskQueuePrio;
	private final LinkedBlockingDeque<_AgentMessage> agentMessages;

	private final AAAAA aaaaa;
	private final ConcurrentHashMap<UUID, AgentInfo> allAgents;
	private final Orphanage orphanage;

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
		this.heartOps = new _HeartOperations();
		this.heart = new Heart(heartOps);
		this.jobScheduler = jsf.create();
		this.agentScheduler = asf.create();

		this.taskQueue = new LinkedBlockingDeque<>();
		this.taskQueuePrio = new LinkedBlockingDeque<>();
		this.agentMessages = new LinkedBlockingDeque<>();

		this.aaaaa = new _AAAAA();
		this.allAgents = new ConcurrentHashMap<>();
		this.orphanage = new Orphanage();

		this.agentListener = new _AgentListener();
		this.actuatorOps = new _ActuatorOperations();

		this.runningJobs = new HashMap<>();

		this.jobScheduler.setJobOperations(new _JobOperations());
		this.agentScheduler.setAgentOperations(new _AgentOperations());

		this.amqp = null;
	}

	/* This runs out-of-band with the state machine. */
	@Override
	public Optional<MessageOperation> processAgentMessage(long tag, AgentMessage msg, byte[] body) throws IllegalStateException {
		if(!agentMessages.offer(new _AgentMessage(tag, msg, Instant.now()))) {
			return Optional.of(MessageOperation.RejectAndRequeue);
		}

		return Optional.empty();
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
	 * @param prio Is this a priority task? Priority tasks are run even when shutting down.
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
					LOGGER.error("Caught exception during LEAVE transition", e);
					state = stateHandlers[state.value].interruptState;
				}
			}

			if(stateHandlers[state.value].handler == null) {
				return false;
			}

			try {
				stateHandlers[state.value].handler.run(state, Mode.Enter);
			} catch(RuntimeException e) {
				LOGGER.error("Caught exception during ENTER transition", e);
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
				LOGGER.error("Caught exception during RUN", e);
				state = stateHandlers[state.value].interruptState;
			}
		}

		return true;
	}

	/*
	 * Register an agent with the master, creating its state machine et al.
	 */
	private AgentInfo registerAgent(DefaultAgentState as, Resource res, Optional<Actuator> act, boolean initial) {
		AgentInfo ai = new AgentInfo(as.getUUID(), res, act, new ReferenceAgent(as, agentListener), as);

		allAgents.put(ai.uuid, ai);

		if(initial) {
			ai.instance.reset(ai.uuid);
		}

		return ai;
	}

	private void checkOrphanage() {
		Map<Resource, Collection<DefaultAgentState>> agentMap = nimrod.getAssignedResources(experiment).stream()
				.collect(Collectors.toMap(
						r -> r,
						r -> nimrod.getResourceAgents(r).stream()
								.map(DefaultAgentState::new)
								.collect(Collectors.toList())
				));

		for(Resource r : agentMap.keySet()) {
			CompletableFuture<Actuator> af = aaaaa.getOrLaunchActuator(r);
			for(DefaultAgentState as : agentMap.get(r)) {
				AgentInfo ai = registerAgent(as, r, Optional.empty(), false);
				heart.onAgentCreate(as.getUUID(), Instant.now());
				heart.resetPingTimer(as.getUUID());
				af.handle((a, t) -> {
					if(t != null) {
						LOGGER.warn("Cannot adopt agent {}, actuator failed to launch.", ai.uuid);
						return null;
					}

					Actuator.AdoptStatus adopt = a.adopt(as);
					if(adopt == Actuator.AdoptStatus.Adopted) {
						LOGGER.info("Resource {} adopted orphaned agent {}.", r.getPath(), as.getUUID());
						ai.actuator.complete(a);
					} else if(adopt == Actuator.AdoptStatus.Stale) {
						LOGGER.info("Resource {} marked orphaned agent {} as stale, expiring...", r.getPath(), as.getUUID());
						orphanage.adopt(as);
						ai.actuator.complete(orphanage);
						runLater("checkOrphanage", () -> Master.this.doExpire(ai), true);
					} else {
						LOGGER.info("Resource {} rejected orphaned agent {}.", r.getPath(), as.getUUID());
						orphanage.adopt(as);
						ai.actuator.complete(orphanage);
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
		Map<Job, Collection<JobAttempt>> activeAttempts = nimrod.filterJobAttempts(experiment,
				EnumSet.of(JobAttempt.Status.NOT_RUN, JobAttempt.Status.RUNNING)
		);

		activeAttempts.forEach((job, value) -> value.stream()
				.filter(att -> att.getStatus() == JobAttempt.Status.RUNNING && !runningJobs.containsKey(att.getAgentUUID()))
				.forEach(att -> {
					AgentInfo ai = allAgents.get(att.getAgentUUID());
					/* If there's no agent associated, something screwy's going on. Fail the attempt and let it reschedule. */
					if(ai == null) {
						/* FIXME: What if the job scheduler actually knows about it and somethings really gone screwy? */
						LOGGER.warn("Active job attempt {} has invalid agent {}, marking as failed.", att.getUUID(), att.getAgentUUID());
						nimrod.finishJobAttempt(att, true);
						return;
					}

					RunningJob rj = new RunningJob(att.getUUID(), job, att, buildNetworkJob(att, job, ai), ai.instance, true);
					runningJobs.put(rj.uuid, rj);
				}));

		/* Resync the schedulers. */
		{
			List<JobAttempt> atts = new ArrayList<>(runningJobs.size());
			List<Job> jobs = new ArrayList<>(runningJobs.size());
			runningJobs.values().forEach(rj -> {
				atts.add(rj.att);
				jobs.add(rj.job);
			});
			jobScheduler.recordAttempts(atts, jobs);
		}

		agentScheduler.resync(
				allAgents.values().stream().map(ai -> ai.instance).collect(Collectors.toSet()),
				new HashSet<>(runningJobs.values())
		);
	}

	private State startProc(State state, Mode mode) {
		if(mode == Mode.Enter) {
			nimrod.updateExperimentState(experiment, Experiment.State.STARTED);

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
				mop = this.doProcessAgentMessage2(msg);
			} catch(IOException | IllegalStateException e) {
				LOGGER.error("Caught exception processing agent message", e);
				continue;
			}

			amqp.opMessage(mop, msg.tag);
		}

		heart.tick(Instant.now());

		{
			List<AgentInfo> ais = heartOps.toExpire.stream()
					.map(allAgents::remove)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			ais.forEach(this::doExpire);

			Map<Resource, List<UUID>> aa = NimrodUtils.mapToParent(ais, ai -> ai.resource, ai -> ai.uuid);
			aa.forEach((k, v) -> aaaaa.runWithActuator(k, a -> a.forceTerminateAgent(v.stream().toArray(UUID[]::new))));

		}
		heartOps.toExpire.clear();
	}

	/* Expire an agent. */
	private void doExpire(AgentInfo ai) {
		if(ai.state.getState() == Agent.State.WAITING_FOR_HELLO) {
			/*
			 * We're still WAITING_FOR_HELLO, ask the actuator what's going on.
			 * This could just be a really slow launch, i.e. stuck in a PBS/SLURM queue.
			 */
			Actuator act;

			try {
				act = ai.actuator.getNow(null);
			} catch(CancellationException | CompletionException e) {
				LOGGER.warn("Actuator future for resource {} failed. This is a bug.", ai.resource.getName(), e);
				act = null;
			}

			if(act == null) {
				/* Actuator isn't ready, the agent gets a pass. */
				return;
			}

			Actuator.AgentStatus status = act.queryStatus(ai.uuid);

			/*
			 * Probably stuck in a queue. Or, if it can't connect back, it'll die
			 * eventually and the state would change to Unknown or Dead.
			 */
			if(status == Actuator.AgentStatus.Launching || status == Actuator.AgentStatus.Launched) {
				return;
			}
		}

		ai.state.setExpired(true);
		runLater("heartExpireAgent", () -> agentScheduler.onAgentExpiry(ai.instance, ai.resource));
		nimrod.updateAgent(ai.state);
	}

	private void processQueue(BlockingDeque<QTask> tasks) {
		/* Use a secondary queue so we don't get stuck in an infinite runSync() loop */
		List<QTask> _tasks = new ArrayList<>();
		tasks.drainTo(_tasks);
		_tasks.forEach(qt -> qt.runnable.run());
	}

	private MessageOperation doProcessAgentMessage2(_AgentMessage _msg) throws IllegalStateException, IOException {
		_msg.processedTime = Instant.now();
		AgentMessage msg = _msg.msg;
		LOGGER.debug("doProcessAgentMessage({}, {})", msg.getAgentUUID(), msg.getType().typeString);

		AgentInfo ai = getAgentInfo(msg.getAgentUUID());
		if(ai == null) {
			LOGGER.warn("Message from unknown agent {}, ignoring...", msg.getAgentUUID());
			return MessageOperation.Ack;
		}

		// TODO: Consider doing an adoption check here
		// TODO: Handle expiry (by checking nimrod)

		if(msg.getType() == AgentMessage.Type.Shutdown && ai.state.getState() == Agent.State.SHUTDOWN) {
			/*
			 * We can ignore a stray shutdown.
			 *
			 * This might also happen when a local agent dies and the actuator can't be sure
			 * it shut down properly.
			 */
			return MessageOperation.Ack;
		}

		/* Process the agent message. */
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
					LOGGER.warn("Unable to kill agent '{}', mimicking successful shutdown.", ai.uuid, e);
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

		nimrod.updateExperimentState(experiment, Experiment.State.STOPPED);
		return State.Stopped;
	}

	private State stoppedProc(State state, Mode mode) {
		return State.None;
	}

	@Override
	public void close() {
		aaaaa.close();
		orphanage.close();
	}

	private class _JobOperations implements JobScheduler.Operations {

		@Override
		public Experiment getExperiment() {
			return experiment;
		}

		@Override
		public Collection<Job> filterJobs(Experiment exp, EnumSet<JobAttempt.Status> status, long start, int limit) {
			return nimrod.filterJobs(exp, status, start, limit);
		}

		@Override
		public JobAttempt.Status fetchJobStatus(Job j) {
			return nimrod.getJobStatus(j);
		}

		@Override
		public Collection<JobAttempt> runJobs(Collection<Job> j) {
			Collection<JobAttempt> att = nimrod.createJobAttempts(j);
			att.forEach(agentScheduler::onJobRun);
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

	/**
	 * Launch result handler.
	 *
	 * It's possible that agents have spawned and are busy before this is run.
	 * This has been seen in situations such as when:
	 * <ul>
	 *     <li>{@link au.edu.uq.rcc.nimrodg.resource.act.LocalActuator} has spawned several subprocesses, and
	 *     them connecting before it's finished, and</li>
	 *     <li>{@link HPCActuator} has submitted a job, but the job's
	 *     started before the submission command has returned.</li>
	 * </ul>
	 *
	 * If an agent has launched successfully and the actuator hasn't, the agent is orphaned.
	 * If an agent has launched successfully and the actuator reports a launch error, the agent is killed.
	 *
	 * @param agentInfo An array of {@link AgentInfo} structures.
	 * @param launchRequest The launch request.
	 */
	private void launchHandler(AgentInfo[] agentInfo, LaunchRequest launchRequest) {
		assert launchRequest.actuatorFuture.isDone();

		/* NB: Submitted by a child of this, so this won't fail. */
		LaunchResult[] lrs = launchRequest.launchResults.join();

		Actuator act;
		if(launchRequest.actuatorFuture.isCompletedExceptionally() || launchRequest.actuatorFuture.isCancelled()) {
			act = orphanage;
		} else {
			act = launchRequest.actuatorFuture.join();
		}

		for(int i = 0; i < lrs.length; ++i) {
			Actuator.LaunchResult lr = lrs[i];
			AgentInfo ai = agentInfo[i];
			ai.actuator.complete(act);

			if(lr.t != null) {
				/* We'll be run during shutdown, so ensure this isn't. */
				runLater("launchAgents->onAgentLaunchFailure", () -> agentScheduler.onAgentLaunchFailure(ai.instance, launchRequest.resource, lr.t), false);

				try {
					ai.instance.terminate();
				} catch(IOException e) {
					LOGGER.error("Unable to terminate agent", e);
				}

				continue;
			}

			ai.state.setActuatorData(lr.actuatorData);
			ai.state.setExpiryTime(lr.expiryTime);

			/* FIXME: Double check this, I might not need to do it here. */
			nimrod.updateAgent(ai.state);

			if(act == orphanage) {
				orphanage.adopt(ai.state);
			}

			/* If the agent's started work before the actuator's done, notify it. */
			if(ai.state.getState() == Agent.State.READY || ai.state.getState() == Agent.State.BUSY) {
				act.notifyAgentConnection(ai.state);
			} else if(ai.state.getState() == Agent.State.SHUTDOWN) {
				act.notifyAgentConnection(ai.state);
				act.notifyAgentDisconnection(ai.uuid);
			}
		}
	}

	private class _AgentOperations implements AgentScheduler.Operations {

		/* Called in the main thread. */
		@Override
		public UUID[] launchAgents(Resource res, int num) {
			if(!nimrod.isResourceAssigned(res, experiment)) {
				throw new IllegalArgumentException();
			}

			UUID[] uuids = AAAAA.generateRandomUUIDs(num);

			/* Create the agents before doing anything, saves us from having to keep a list of pending connections. */
			AgentInfo[] agentInfo = new AgentInfo[num];
			for(int i = 0; i < num; ++i) {
				DefaultAgentState as = new DefaultAgentState();
				as.setUUID(uuids[i]);
				agentInfo[i] = registerAgent(as, res, Optional.empty(), true);
			}

			/* NB: Will not block. */
			LaunchRequest rq = aaaaa.launchAgents(res,
					Arrays.stream(uuids).map(Actuator.Request::forAgent).toArray(Actuator.Request[]::new)
			);

			rq.launchResults.thenAcceptAsync(lrs -> runLater(
					"launchAgents->handler",
					() -> launchHandler(agentInfo, rq), true), aaaaa.getExecutorService()
			);

			return uuids;
		}

		@Override
		public void terminateAgent(Agent agent) {
			runLater("agentDoTerminateAgent", () -> {
				LOGGER.trace("Termination of agent '{}' requested.", agent.getUUID());
				try {
					agent.terminate();
				} catch(IOException e) {
					LOGGER.trace("Termination failed.", e);
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
				LOGGER.error("Unable to submit job '{}' on agent '{}'", job.getPath(), agent.getUUID(), e);
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
			return nimrod.getResources();
		}

		@Override
		public Collection<Resource> getAssignedResources(Experiment exp) {
			return nimrod.getAssignedResources(exp);
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
		public void send(Agent agent, AgentMessage.Builder<?> msg) throws IOException {
			amqp.sendMessage(agent.getQueue(), msg.build());
		}

		@Override
		public void onStateChange(Agent agent, Agent.State oldState, Agent.State newState) {
			AgentInfo ai = getAgentInfo(agent);
			assert agent == ai.instance;

			LOGGER.debug("Agent {}: State change from {} -> {}", agent.getUUID(), oldState, newState);

			if(oldState == null) {
				assert newState == Agent.State.WAITING_FOR_HELLO;
				AgentState nas = nimrod.addAgent(ai.resource, ai.state);
				ai.state.update(nas);
				heart.onAgentCreate(ai.uuid, Instant.now());
			} else if(oldState == Agent.State.WAITING_FOR_HELLO && newState == Agent.State.READY) {
				ai.state.setConnectionTime(Instant.now());
				aaaaa.runWithActuator(ai.resource, a -> a.notifyAgentConnection(ai.state));
				nimrod.updateAgent(ai.state);
			} else if(newState == Agent.State.SHUTDOWN) {
				ai.state.setExpired(true);
				aaaaa.runWithActuator(ai.resource, a -> a.notifyAgentDisconnection(ai.uuid));
				heart.onAgentDisconnect(ai.uuid);
				allAgents.remove(ai.uuid);
				nimrod.updateAgent(ai.state);
			}

			/* Execute this with priority so it's processed before the next scheduler tick. */
			runLater("agOnStateChange", () -> agentScheduler.onAgentStateUpdate(agent, ai.resource, oldState, newState), true);
		}

		@Override
		public void onJobSubmit(Agent agent, NetworkJob job) {
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

	private class _ActuatorOperations extends ActuatorOpsAdapter {
		public _ActuatorOperations() {
			super(Master.this.nimrod);
		}

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {
			Instant now = Instant.now();
			// FIXME: Should probably be some way to tell this message was faked.
			agentMessages.offer(new _AgentMessage(-1, new AgentShutdown.Builder()
					.agentUuid(uuid)
					.reason(reason)
					.signal(signal)
					.build(),
					now));
		}

	}

	private class _HeartOperations implements Heart.Operations {

		public final Logger LOGGER = LoggerFactory.getLogger(Heart.class);
		public final Set<UUID> toExpire;

		public _HeartOperations() {
			this.toExpire = new HashSet<>();
		}

		@Override
		public void expireAgent(UUID u) {
			toExpire.add(u);
		}

		@Override
		public void terminateAgent(UUID u) {
			try {
				allAgents.get(u).instance.terminate();
			} catch(IOException e) {
				LOGGER.error("Unable to terminate agent {}", u, e);
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
				LOGGER.error("Unable to ping agent {}", u, e);
			}
		}

		@Override
		public Instant getLastHeardFrom(UUID u) {
			AgentState as = allAgents.get(u).state;
			/* This doesn't necessarily need to be last heard from, it just needs a reference point for the heartbeats. */
			return NimrodUtils.coalesce(
					as.getLastHeardFrom(),
					as.getConnectionTime(),
					as.getCreationTime()
			);
		}

		@Override
		public Instant getWalltime(UUID u) {
			return allAgents.get(u).state.getExpiryTime();
		}

		@Override
		public void logInfo(String fmt, Object... args) {
			LOGGER.info("{}", String.format(fmt, args));
		}

		@Override
		public void logTrace(String fmt, Object... args) {
			//LOGGER.trace("{}", String.format(fmt, args));
		}

	}
}
