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
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.events.ConfigChangeMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.JobAddMasterEvent;
import au.edu.uq.rcc.nimrodg.api.events.NimrodMasterEvent;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.master.sched.AgentScheduler;
import au.edu.uq.rcc.nimrodg.master.sched.JobScheduler;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.master.AAAAA.LaunchRequest;
import au.edu.uq.rcc.nimrodg.master.MessageQueueListener.MessageOperation;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.master.sched.DefaultAgentScheduler;
import au.edu.uq.rcc.nimrodg.master.sched.DefaultJobScheduler;
import java.util.Optional;

/**
 * The Nimrod/G experiment master.
 *
 * Things to remember:
 * <ul>
 * <li>Agent state machine access is synchronised on the {@link Agent} object.</li>
 * <li>If an actuator dies, all its agents die with it.</li>
 * <li>Methods starting with <em>ag</em> are {@link ReferenceAgent} callbacks.</li>
 * <li>Methods starting with <em>agentDo</em> are {@link AgentScheduler} callbacks.</li>
 * <li>Methods starting with <em>jobDo</em> are {@link JobScheduler} callbacks.</li>
 * <li>All agent and scheduler callbacks are called on the main thread.</li>
 * </ul>
 */
public class Master implements AutoCloseable, MessageQueueListener {

	private static final Logger LOGGER = LogManager.getLogger(Master.class);
	private static final int DEFFAULT_RESCAN_INTERVAL = 60;

	private class RunningJob {

		public final UUID uuid;
		public final JobAttempt att;
		public final NetworkJob job;
		public final Agent agent;

		public RunningJob(UUID uuid, JobAttempt att, NetworkJob job, Agent agent) {
			this.uuid = uuid;
			this.att = att;
			this.job = job;
			this.agent = agent;
		}
	}

	public enum State {
		NEW,
		STARTING,
		STARTED,
		STOPPING,
		STOPPED
	}

	private final NimrodMasterAPI m_Nimrod;
	private final Experiment m_Experiment;

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

	/*
	 * The priority task queue. Run before the main task queue. This should
	 * only be used for tasks that MUST be run before any queued scheduler tasks
	 * such as agent state updates.
	 */
	private final LinkedBlockingDeque<QTask> m_PrioTaskQueue;
	/* The default task queue. Run before the schedulers are ticked. */
	private final LinkedBlockingDeque<QTask> m_TaskQueue;

	private final AAAAA m_AAAAA;
	private final Map<UUID, RunningJob> m_ManagedJobs;
	private final Map<UUID, RunningJob> m_UnmanagedJobs;
	private final Set<Agent> m_ManagedAgents;
	private final ConcurrentHashMap<UUID, AgentInfo> m_AllAgents;
	private final ConcurrentHashMap<UUID, LaunchRequest> m_PendingAgentNodes;

	private final _AgentListener m_AgentListener;
	private final JobScheduler m_JobScheduler;
	private final AgentScheduler m_AgentScheduler;
	private final _ActuatorOperations m_ActuatorOps;

	private State m_State;

	private final AtomicBoolean m_StopFlag;
	private final List<NimrodMasterEvent> m_PendingEvents;
	private final LinkedBlockingDeque<_AgentMessage> m_PendingMessages;

	private final List<CompletableFuture> m_ShutdownFutures;
	private final Set<AgentInfo> m_ShutdownFailedAgents;

	private AMQProcessor m_AMQP;
	private final Heart m_Heart;

	public Master(NimrodMasterAPI nimrod, Experiment exp) throws IOException, TimeoutException, URISyntaxException, GeneralSecurityException {
		this(nimrod, exp, DefaultJobScheduler.FACTORY, DefaultAgentScheduler.FACTORY);
	}

	public Master(NimrodMasterAPI nimrod, Experiment exp, JobSchedulerFactory jsf, AgentSchedulerFactory asf) throws IOException, TimeoutException, URISyntaxException, GeneralSecurityException {
		m_Nimrod = nimrod;
		m_Experiment = exp;

		m_PrioTaskQueue = new LinkedBlockingDeque<>();
		m_TaskQueue = new LinkedBlockingDeque<>();

		{
			m_ManagedJobs = new HashMap<>();
			m_UnmanagedJobs = new HashMap<>();
			m_ManagedAgents = new HashSet<>();
			m_AllAgents = new ConcurrentHashMap<>();
			m_PendingAgentNodes = new ConcurrentHashMap<>();
		}

		m_AgentListener = new _AgentListener();
		m_JobScheduler = jsf.create();
		m_AgentScheduler = asf.create();
		m_ActuatorOps = new _ActuatorOperations();

		m_JobScheduler.setJobOperations(new _JobOperations());
		m_AgentScheduler.setAgentOperations(new _AgentOperations());

		m_State = State.NEW;

		m_AAAAA = new AAAAA(5) {
			@Override
			protected void reportLaunchFailure(UUID uuid, Resource node, Throwable t) {
				m_AgentScheduler.onAgentLaunchFailure(uuid, node, t);
			}

			@Override
			protected Actuator createActuator(Resource root) throws IOException, IllegalArgumentException {
				NimrodURI amqpUri = root.getAMQPUri();
				Certificate[] certs;
				try {
					certs = ActuatorUtils.readX509Certificates(amqpUri.certPath);
				} catch(CertificateException e) {
					throw new IOException(e);
				}

				return m_Nimrod.createActuator(m_ActuatorOps, root, certs);
			}
		};

		m_StopFlag = new AtomicBoolean(false);
		m_PendingEvents = new ArrayList<>();
		m_PendingMessages = new LinkedBlockingDeque<>();
		m_ShutdownFutures = new ArrayList<>();
		m_ShutdownFailedAgents = new HashSet<>();

		m_AMQP = null;
		m_Heart = new Heart(new _HeartOperations());
	}

	public void setAMQP(AMQProcessor amqp) {
		if(amqp == null || m_AMQP != null) {
			throw new IllegalStateException();
		}

		m_AMQP = amqp;
	}

	private void runLaterSync(String name, Runnable r, boolean prio) {
		if(prio) {
			m_PrioTaskQueue.offer(new QTask(name, r));
		} else {
			m_TaskQueue.offer(new QTask(name, r));
		}
	}

	private void runLaterSync(String name, Runnable r) {
		runLaterSync(name, r, false);
	}

	@Override
	public void close() {
		m_AAAAA.close();
	}

	private State setState(State newState) {
		State oldState = m_State;
		m_State = newState;
		onStateChange(oldState, newState);
		LOGGER.info("State change from {} -> {}", oldState, newState);
		return oldState;
	}

	public void flagStop() {
		m_StopFlag.set(true);
	}

	public boolean tick() {
		if(m_State == State.NEW) {
			setState(State.STARTING);
			return true;
		}

		if(m_State == State.STARTING) {
			setState(State.STARTED);
			return true;
		}

		if(m_State == State.STOPPED) {
			return false;
		}

		/* Only poll the database if we're started. */
		if(m_State == State.STARTED) {
			if(m_StopFlag.get()) {
				setState(State.STOPPING);
				return true;
			}

			//LOGGER.trace("m_Nimrod.pollMasterEvents()");
			m_PendingEvents.addAll(m_Nimrod.pollMasterEvents());
			for(NimrodMasterEvent evt : m_PendingEvents) {
				try {
					processEvent(evt);
				} catch(Exception e) {
					LOGGER.warn("Caught exception during event processing.");
					LOGGER.catching(e);
				}
			}

			m_PendingEvents.clear();

		}

		/* Always process agent messages. */
		{
			List<_AgentMessage> msgs = new ArrayList<>();
			m_PendingMessages.drainTo(msgs);

			for(_AgentMessage msg : msgs) {
				MessageOperation mop;
				try {
					mop = this.doProcessAgentMessage2(msg);
				} catch(IOException | IllegalStateException e) {
					LOGGER.error("Caught exception processing agent message ");
					LOGGER.catching(e);
					continue;
				}

				/* Requeue the message if the handler bounced it */
				if(mop == MessageOperation.RejectAndRequeue) {
					m_PendingMessages.offer(msg);
				} 
			}
		}

		/* Always process agent heartbeats, timeouts, etc. */
		m_Heart.tick(Instant.now());

		if(m_State == State.STARTED) {
			/* Use a secondary queue so we don't get stuck in an infinite runSync() loop */
			List<QTask> tasks = new ArrayList<>();
			m_PrioTaskQueue.drainTo(tasks);
			m_TaskQueue.drainTo(tasks);
			for(QTask qt : tasks) {
				//LOGGER.trace("Executing task from {}", qt.callerName);
				try {
					qt.runnable.run();
				} catch(Throwable t) {
					LOGGER.catching(t);
					setState(State.STOPPING);
					return true;
				}
			}

			//LOGGER.trace("m_JobScheduler.tick()");
			try {
				if(!m_JobScheduler.tick()) {
					m_StopFlag.set(true);
				}
			} catch(RuntimeException e) {
				LOGGER.error("Caught exception in job scheduler. Exiting...");
				LOGGER.catching(e);
				setState(State.STOPPING);
				return true;
			}

			//LOGGER.trace("m_AgentScheduler.tick()");
			try {
				if(!m_AgentScheduler.tick()) {
					m_StopFlag.set(true);
				}
			} catch(RuntimeException e) {
				LOGGER.error("Caught exception in agent scheduler. Exiting...");
				LOGGER.catching(e);
				setState(State.STOPPING);
				return true;
			}
		}

		if(m_State == State.STOPPING) {
			//LOGGER.trace("SS: Waiting for all agents to die.");

			if(m_ShutdownFutures.isEmpty()) {
				/* Anything left in failedAgents can just die */
				m_ShutdownFailedAgents.forEach(a -> a.instance.disconnect(AgentShutdown.Reason.Requested, -1));
				setState(State.STOPPED);
				return false;
			}

			m_ShutdownFutures.removeIf(f -> f.isDone());

		}

		return true;
	}

	private void onStateChange(State oldState, State newState) {
		if(oldState == State.NEW) {
			if(newState != State.STARTING) {
				throw new IllegalStateException();
			}

			/* Create psuedo-events for the initial configuration values. */
			m_Nimrod.getProperties().entrySet().stream()
					.map(e -> new ConfigChangeMasterEvent(e.getKey(), null, e.getValue()))
					.forEach(m_PendingEvents::add);

		} else if(oldState == State.STARTED && newState == State.STOPPING) {
			/* Kill any pending launches */
			m_ShutdownFutures.add(m_AAAAA.shutdown());

			/* Send termination requests to all the agents. */
			for(AgentInfo ai : m_AllAgents.values()) {
				try {
					LOGGER.trace("SS: Terminating agent '{}'", ai.uuid);
					ai.instance.terminate();
				} catch(IOException e) {
					m_ShutdownFailedAgents.add(ai);
					LOGGER.warn("Unable to kill agent '{}', {}", ai.uuid, e.getMessage());
					LOGGER.catching(e);
				}
			}

			/* Wait for m_AllAgents to be empty. */
			m_ShutdownFutures.add(CompletableFuture.runAsync(() -> {
				synchronized(m_AllAgents) {
					HashSet<AgentInfo> ass = new HashSet<>();

					while(true) {
						/* Remove any that just so happened to shutdown even if sending the message failed. Thanks computer god. */
						m_ShutdownFailedAgents.removeIf(a -> !m_AllAgents.containsValue(a));

						/* Filter the failed ones from the pool. */
						ass.clear();
						ass.addAll(m_AllAgents.values());
						ass.removeAll(m_ShutdownFailedAgents);

						if(ass.isEmpty()) {
							break;
						}

						try {
							m_AllAgents.wait();
						} catch(InterruptedException e) {
							// nop
						}
					}
				}

				int x = 0;
			}));
		} else if(oldState == State.STOPPING && newState == State.STOPPED) {
			m_ShutdownFutures.clear();
			m_ShutdownFailedAgents.clear();
		}
	}

	private static int parseOrDefault(String val, int d) {
		int ret = d;
		try {
			if(val != null) {
				ret = Integer.parseInt(val);
			}
		} catch(NumberFormatException e) {
			ret = d;
		}

		return ret;
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

				m_JobScheduler.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				m_AgentScheduler.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				m_Heart.onConfigChange(evt.key, evt.oldValue, evt.newValue);
				break;
			}

			case JobAdd: {
				JobAddMasterEvent evt = (JobAddMasterEvent)_evt;
				m_JobScheduler.onJobAdd(evt.job);
				break;
			}
		}
	}

	// <editor-fold defaultstate="collapsed" desc="Heartbeat Operations">
	/* Heart is run directly after agent message processing, so it's safe to do direct agent stuff here. */
	private void heartExpireAgent(UUID u) {
		AgentInfo ai = m_AllAgents.remove(u);
		ai.state.setExpired(true);
		ai.actuator.forceTerminateAgent(u);
		ai.actuator.notifyAgentDisconnection(u);
		runLaterSync("heartExpireAgent", () -> m_AgentScheduler.onAgentExpiry(u));
		m_Nimrod.updateAgent(ai.state);
	}

	private void heartTerminateAgent(UUID u) {
		try {
			m_AllAgents.get(u).instance.terminate();
		} catch(IOException e) {
			LOGGER.catching(e);
		}
	}

	private void heartDisconnectAgent(UUID u, AgentShutdown.Reason reason, int signal) {
		m_AllAgents.get(u).instance.disconnect(reason, signal);
	}

	private void heartPingAgent(UUID u) {
		try {
			m_AllAgents.get(u).instance.ping();
		} catch(IOException e) {
			LOGGER.catching(e);
		}
	}

	private Instant heartGetLastHeartFrom(UUID u) {
		return m_AllAgents.get(u).state.getLastHeardFrom();
	}

	private Instant heartGetExpiryTime(UUID u) {
		return m_AllAgents.get(u).state.getExpiryTime();
	}

	// </editor-fold>
	// <editor-fold defaultstate="collapsed" desc="AgentScheduler Operations">
	private UUID[] agentDoLaunchAgents(Resource node, int num) {
		// TODO: Check the resource has actually been added

		UUID[] uuids = new UUID[num];
		for(int i = 0; i < uuids.length; ++i) {
			uuids[i] = UUID.randomUUID();
		}

		if(m_State != State.STARTED) {
			LOGGER.info("Ignoring agent launch, shutting down...");
			return uuids;
		}

		LaunchRequest rq = m_AAAAA.launchAgents(node, num);
		Stream.of(rq.uuids).forEach(u -> m_PendingAgentNodes.put(u, rq));
		return rq.uuids;
	}

	private void agentDoTerminateAgent(Agent agent) {
		runLaterSync("agentDoTerminateAgent", () -> {
			LOGGER.trace("Termination of agent '{}' requested.", agent.getUUID());
			try {
				agent.terminate();
			} catch(IOException e) {
				LOGGER.trace("Termination failed.");
				LOGGER.catching(e);
			}
		});
	}

	private UUID agentDoRunJob(JobAttempt att, Agent agent) {
		Job job = att.getJob();
		LOGGER.info("Run job '{}' on agent '{}'", job.getPath(), agent.getUUID());

		UUID uuid = att.getUUID();

		final NetworkJob nj;
		try {
			/* FIXME: handle cert path, etc. */
			Optional<NimrodURI> txUri = m_Nimrod.getAssignmentStatus(agentDoGetAgentResource(agent), m_Experiment);
			if(!txUri.isPresent()) {
				throw new IllegalStateException();
			}
			nj = MsgUtils.resolveJob(uuid, job, Task.Name.Main, txUri.map(u -> u.uri).get(), m_Nimrod.getJobAttemptToken(att));
		} catch(IllegalArgumentException e) {
			/*
			 * This is a resolution failure. This indicates an underlying issue with the job that wasn't
			 * picked up during compilation.
			 *
			 * Notify the agent scheduler, then notify the job scheduler which will decide what to do.
			 */
			runLaterSync("agentDoRunJob(resolutionFailure)", () -> {
				m_AgentScheduler.onJobLaunchFailure(att, null, agent, true, e);
				m_JobScheduler.onJobLaunchFailure(att, null, e);
			});
			return uuid;
		}

		Throwable t = null;
		try {
			agent.submitJob(nj);
		} catch(IOException | IllegalArgumentException | IllegalStateException e) {
			t = e;
			LOGGER.catching(Level.WARN, e);
		}

		if(t == null) {
			m_ManagedAgents.add(agent);
			m_ManagedJobs.put(uuid, new RunningJob(uuid, att, nj, agent));
			runLaterSync("agentDoRunJob(js:onJobLaunchSuccess)", () -> m_JobScheduler.onJobLaunchSuccess(att, agent.getUUID()));
		} else {
			/* This is an agent issue, try reschedule the job. */
			Throwable _t = t;
			runLaterSync("agentDoRunJob(as:onJobLaunchFailure)", () -> m_AgentScheduler.onJobLaunchFailure(att, nj, agent, false, _t));
		}

		return uuid;
	}

	private void agentDoRunUnmanagedJob(NetworkJob job, Agent agent) {
		runLaterSync("agentDoRunUnmanagedJob", () -> {
			if(m_UnmanagedJobs.containsKey(job.uuid)) {
				throw new IllegalArgumentException();
			}

			try {
				synchronized(agent) {
					agent.submitJob(job);
				}
			} catch(IOException | IllegalArgumentException | IllegalStateException e) {
				runLaterSync("agentDoRunUnmanagedJob", () -> m_AgentScheduler.onJobLaunchFailure(null, job, agent, true, e));
				return;
			}

			m_UnmanagedJobs.put(job.uuid, new RunningJob(job.uuid, null, job, agent));
		});
	}

	private void agentDoCancelCurrentJob(Agent agent) {
		//runLater(() -> agent.cancelJob());
	}

	private Collection<Resource> agentDoGetResources() {
		return m_Nimrod.getResources().stream().map(n -> (Resource)n).collect(Collectors.toList());
	}

	private Collection<Resource> agentDoGetAssignedResources(Experiment exp) {
		return m_Nimrod.getAssignedResources(exp).stream().map(n -> (Resource)n).collect(Collectors.toList());
	}

	private boolean agentDoIsResourceCapable(Resource node, Experiment exp) {
		return m_Nimrod.isResourceCapable(node, exp);
	}

	private Optional<NimrodURI> agentDoResolveTransferUri(Resource res, Experiment exp) {
		return m_Nimrod.getAssignmentStatus(res, exp);
	}

	private void agentDoAddResourceCaps(Resource node, Experiment exp) {
		m_Nimrod.addResourceCaps(node, exp);
	}

	private Resource agentDoGetAgentResource(Agent agent) {
		return m_Nimrod.getAgentResource(agent.getUUID());
	}

	private List<Agent> agentDoGetResourceAgents(Resource node) {
		/* Get agents, ignoring ones we don't have an instance for. */
		return m_Nimrod.getResourceAgents(node)
				.stream()
				.map(s -> getAgentInfo(s.getUUID()))
				.filter(ai -> ai != null)
				.map(ai -> ai.instance)
				.collect(Collectors.toList());
	}

	private void agentReportJobFailure(JobAttempt att, Agent agent, AgentScheduler.Operations.FailureReason reason) {
		LOGGER.trace("agentReportJobFailure() called, att = {}, agent = {}, reason = {}", agent.getUUID(), att.getUUID(), reason);
		runLaterSync("agentReportJobFailure", () -> m_JobScheduler.onJobFailure(att, reason));
	}

	// </editor-fold>
	// <editor-fold defaultstate="collapsed" desc="JobScheduler Operations">
	private Experiment jobDoGetExperiment() {
		return m_Experiment;
	}

	private JobAttempt jobDoRunJob(Job j) {
		/* Called by job scheduler */
		LOGGER.info("Run of job '{}' requested", j.getPath());
		JobAttempt att = m_Nimrod.createJobAttempt(j);
		m_AgentScheduler.onJobRun(att);
		return att;
	}

	private void jobDoUpdateExperimentState(Experiment.State state) {
		LOGGER.info("Experiment state change to {}", state);
		m_Nimrod.updateExperimentState(m_Experiment, state);
	}

	private void jobDoCancelJob(JobAttempt j) {
		/* Called by job scheduler */
		m_AgentScheduler.onJobCancel(j);
	}

	private void jobDoUpdateJobStarted(JobAttempt att, UUID agentUuid) {
		m_Nimrod.startJobAttempt(att, agentUuid);
	}

	private void jobDoUpdateJobFinished(JobAttempt att, boolean failed) {
		m_Nimrod.finishJobAttempt(att, failed);
	}

	private void jobDoRecordCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) {
		m_Nimrod.addCommandResult(att, status, index, time, retval, message, errcode, stop);
	}

	// </editor-fold>
	// <editor-fold defaultstate="collapsed" desc="Actuator Operations">
	private void actReportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {
		LOGGER.trace("actReportAgentFailure({}, {}, {})", uuid, reason, signal);
		if(uuid == null) {
			throw new IllegalArgumentException();
		}

		AgentInfo ai = getAgentInfo(uuid);
		if(ai == null) {
			return;
		}

		if(ai.actuator != act) {
			throw new IllegalArgumentException();
		}

		if(!m_PendingMessages.offer(new _AgentMessage(new AgentShutdown(uuid, reason, signal), Instant.now()))) {
			/* Not much we can do here, the master will hang. */
//			/* #yolo */
//			ai.instance.disconnect();
//			synchronized(m_AllAgents) {
//				m_AllAgents.notify();
//			}
		}
	}

	private NimrodMasterAPI actGetNimrod() {
		return m_Nimrod;
	}

	// <editor-fold>
	// <editor-fold defaultstate="collapsed" desc="Agent Procs">
	private void agSend(Agent agent, AgentMessage msg) throws IOException {
		m_AMQP.sendMessage(agent.getQueue(), msg);
	}

	private void agOnStateChange(Agent _agent, Agent.State oldState, Agent.State newState) {
		if(oldState == null) {
			/* Initial state setup, we don't do anything here. */
			return;
		}

		LOGGER.debug("Agent {}: State change from {} -> {}", _agent.getUUID(), oldState, newState);

		AgentInfo ai = getAgentInfo(_agent);
		assert _agent == ai.instance;

		if(oldState == Agent.State.WAITING_FOR_HELLO && newState == Agent.State.READY) {
			ai.state.setCreationTime(Instant.now());
			ai.actuator.notifyAgentConnection(ai.state);
			m_Nimrod.addAgent(ai.node, ai.state);
			m_Heart.onAgentConnect(ai.uuid);
		} else {
			if(newState == Agent.State.SHUTDOWN) {
				ai.state.setExpired(true);
			}
			m_Nimrod.updateAgent(ai.state);

			if(newState == Agent.State.SHUTDOWN) {
				ai.actuator.notifyAgentDisconnection(ai.uuid);
				m_Heart.onAgentDisconnect(ai.uuid);
				m_AllAgents.remove(ai.uuid);
				synchronized(m_AllAgents) {
					m_AllAgents.notify();
				}
			}
		}

		/* Execute this with priority so it's processed before the next scheduler tick. */
		runLaterSync("agOnStateChange", () -> m_AgentScheduler.onAgentStateUpdate(_agent, ai.node, oldState, newState), true);
	}

	private AgentInfo getAgentInfo(UUID uuid) {
		return m_AllAgents.getOrDefault(uuid, null);
	}

	private AgentInfo getAgentInfo(Agent agent) {
		return getAgentInfo(agent.getUUID());
	}

	private void agOnJobSubmit(Agent agent, AgentSubmit as) {
		/* nop */
	}

	private void agOnJobUpdate(Agent agent, AgentUpdate au) {
		UUID uuid = au.getJobUUID();

		boolean isManaged;
		RunningJob rj;
		/* See if we're an unmanaged agent */
		if(!m_ManagedAgents.contains(agent)) {
			rj = m_UnmanagedJobs.get(uuid);
			isManaged = false;
		} else {
			rj = m_ManagedJobs.get(uuid);
			isManaged = true;
		}

		assert rj.agent.equals(agent);
		if(isManaged) {
			/* Managed job, it's for the job scheduler */
			runLaterSync("agOnJobUpdate", () -> {
				m_JobScheduler.onJobUpdate(rj.att, au, rj.job.numCommands);
			});
		} else {
			/* Unmanaged job, it's for the agent scheduler */
			runLaterSync("agOnJobUpdate", () -> {
				m_AgentScheduler.onUnmanagedJobUpdate(rj.job, au, agent);
			});
		}
	}

	private void agOnPong(Agent agent, AgentPong pong) {
		m_Heart.onAgentPong(agent.getUUID());
		m_Nimrod.updateAgent(((ReferenceAgent)agent).getDataStore());
	}

	@Override
	public MessageOperation processAgentMessage(AgentMessage msg) {
		if(!m_PendingMessages.offer(new _AgentMessage(msg, Instant.now()))) {
			return MessageOperation.RejectAndRequeue;
		}
		return MessageOperation.Ack;
	}

	private MessageOperation doProcessAgentMessage2(_AgentMessage _msg) throws IllegalStateException, IOException {
		/* A flag. If this is set, then the agent killis new and needs to be added to the tree. */
		LaunchRequest _info = null;

		_msg.processedTime = Instant.now();
		AgentMessage msg = _msg.msg;
		LOGGER.debug("doProcessAgentMessage({}, {})", msg.getAgentUUID(), msg.getTypeString());

		AgentState agentState;
		/* If we're an agent.hello, validate and register it. */
		if(msg.getType() == AgentMessage.Type.Hello) {
			AgentHello hello = (AgentHello)msg;
			LOGGER.trace("Received agent.hello with (uuid, queue) = ({}, {})", hello.getAgentUUID(), hello.queue);

			if(m_State != State.STARTED) {
				LOGGER.warn("Agent connection after close(), terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}

			if((_info = m_PendingAgentNodes.remove(hello.getAgentUUID())) == null) {
				LOGGER.warn("Agent connection unexpected, terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}

			/*
			 * If our future's not done, defer the message.
			 * This happens sometimes with PBS, the jobs start before qsub's returned.
			 */
			if(!_info.future.isDone()) {
				LOGGER.debug("Agent connected, but actuator hasn't finished, deferring...");
				m_PendingAgentNodes.put(hello.getAgentUUID(), _info);
				return MessageQueueListener.MessageOperation.RejectAndRequeue;
			}

			/* See if Nimrod knows about the agent. */
			agentState = m_Nimrod.getAgentByUUID(msg.getAgentUUID());
			if(agentState != null) {
				if(agentState.getQueue().equals(hello.queue)) {
					/* Agent is misbehaving, it's sent a superflous agent.hello. Continue on. */
				} else {
					/* Okay, there's actually a duplicate UUID. Dafuq? */
					LOGGER.warn("Agent connection with duplicate UUID, buy a lottery ticket.");
					return MessageQueueListener.MessageOperation.Terminate;
				}
			} else {
				agentState = new DefaultAgentState();
			}
		} else {
			/* If we're not an agent.hello, we should already be registered with the API. This includes expired agents. */
			agentState = m_Nimrod.getAgentByUUID(msg.getAgentUUID());
			if(agentState == null) {
				LOGGER.warn("Received message from unknown agent, terminating...");
				return MessageQueueListener.MessageOperation.Terminate;
			}
		}

		AgentInfo ai = getAgentInfo(msg.getAgentUUID());
		/* No agent instance, this is either a "new" agent (from an agent.hello), or a reconnection. */
		if(ai == null) {
			if(_info != null) {
				/* This is from an agent.hello. */
			} else {
				/* This must be a a stray message or a reconnection. */
				if(msg.getType() == AgentMessage.Type.Shutdown) {
					/*
					 * We can ignore a stray shutdown.
					 *
					 * This might also happen when a local agent dies and the actuator can't be sure
					 * it shut down properly.
					 */
					return MessageQueueListener.MessageOperation.Terminate;
				}
				//FIXME: Actually handle this;
				LOGGER.error("NOT HANDLING RECONNECTION, FIX ME PLS: {}", msg.getTypeString());
				return MessageQueueListener.MessageOperation.Terminate;
			}
			/* getNow(null) will never fail here. */
			Resource[] batchNodes = _info.future.getNow(null);
			Actuator.LaunchResult[] launchResults = _info.launchResults.getNow(null);

			UUID agentUuid = msg.getAgentUUID();
			int batchIndex = -1;
			{
				for(int i = 0; i < _info.uuids.length; ++i) {
					if(_info.uuids[i] == null) {
						continue;
					}

					if(_info.uuids[i].equals(agentUuid)) {
						batchIndex = i;
						break;
					}
				}
			}

			if(batchIndex < 0) {
				throw new IllegalStateException("batchIndex < 0, this should never happen");
			}
			agentState.setExpiryTime(launchResults[batchIndex].expiryTime);
			ai = new AgentInfo(msg.getAgentUUID(), batchNodes[batchIndex], _info.node, _info.actuatorFuture.getNow(null), new ReferenceAgent(agentState, m_AgentListener), agentState);
			m_AllAgents.put(ai.uuid, ai);
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

	// </editor-fold>
	// <editor-fold defaultstate="collapsed" desc="Listeners">
	private class _JobOperations implements JobScheduler.Operations {

		@Override
		public Experiment getExperiment() {
			return Master.this.jobDoGetExperiment();
		}

		@Override
		public JobAttempt runJob(Job j) {
			return Master.this.jobDoRunJob(j);
		}

		@Override
		public void updateExperimentState(Experiment.State state) {
			Master.this.jobDoUpdateExperimentState(state);
		}

		@Override
		public void cancelJob(JobAttempt j) {
			Master.this.jobDoCancelJob(j);
		}

		@Override
		public void updateJobStarted(JobAttempt att, UUID agentUuid) {
			Master.this.jobDoUpdateJobStarted(att, agentUuid);
		}

		@Override
		public void updateJobFinished(JobAttempt att, boolean finish) {
			Master.this.jobDoUpdateJobFinished(att, finish);
		}

		@Override
		public void recordCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop) {
			Master.this.jobDoRecordCommandResult(att, status, index, time, retval, message, errcode, stop);
		}

	}

	private class _AgentOperations implements AgentScheduler.Operations {

		@Override
		public UUID[] launchAgents(Resource res, int num) {
			return Master.this.agentDoLaunchAgents(res, num);
		}

		@Override
		public void terminateAgent(Agent agent) {
			Master.this.agentDoTerminateAgent(agent);
		}

		@Override
		public UUID runJob(JobAttempt att, Agent agent) {
			return Master.this.agentDoRunJob(att, agent);
		}

		@Override
		public void runUnmanagedJob(NetworkJob job, Agent agent) {
			Master.this.agentDoRunUnmanagedJob(job, agent);
		}

		@Override
		public void cancelCurrentJob(Agent agent) {
			Master.this.agentDoCancelCurrentJob(agent);
		}

		@Override
		public Collection<Resource> getResources() {
			return Master.this.agentDoGetResources();
		}

		@Override
		public Collection<Resource> getAssignedResources(Experiment exp) {
			return Master.this.agentDoGetAssignedResources(exp);
		}

		@Override
		public boolean isResourceCapable(Resource node, Experiment exp) {
			return Master.this.agentDoIsResourceCapable(node, exp);
		}

		@Override
		public Optional<NimrodURI> resolveTransferUri(Resource res, Experiment exp) {
			return Master.this.agentDoResolveTransferUri(res, exp);
		}

		@Override
		public void addResourceCaps(Resource node, Experiment exp) {
			Master.this.agentDoAddResourceCaps(node, exp);
		}

		@Override
		public Resource getAgentResource(Agent agent) {
			return Master.this.agentDoGetAgentResource(agent);
		}

		@Override
		public List<Agent> getResourceAgents(Resource node) {
			return Master.this.agentDoGetResourceAgents(node);
		}

		@Override
		public void reportJobFailure(JobAttempt att, Agent agent, AgentScheduler.Operations.FailureReason reason) {
			Master.this.agentReportJobFailure(att, agent, reason);
		}
	}

	private class _AgentListener implements ReferenceAgent.AgentListener {

		@Override
		public void send(Agent agent, AgentMessage msg) throws IOException {
			Master.this.agSend(agent, msg);
		}

		@Override
		public void onStateChange(Agent agent, Agent.State oldState, Agent.State newState) {
			Master.this.agOnStateChange(agent, oldState, newState);
		}

		@Override
		public void onJobSubmit(Agent agent, AgentSubmit as) {
			Master.this.agOnJobSubmit(agent, as);
		}

		@Override
		public void onJobUpdate(Agent agent, AgentUpdate au) {
			Master.this.agOnJobUpdate(agent, au);
		}

		@Override
		public void onPong(Agent agent, AgentPong pong) {
			Master.this.agOnPong(agent, pong);
		}
	}

	private class _ActuatorOperations implements Actuator.Operations {

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {
			Master.this.actReportAgentFailure(act, uuid, reason, signal);
		}

		@Override
		public NimrodMasterAPI getNimrod() {
			return Master.this.actGetNimrod();
		}
	}

	private class _HeartOperations implements Heart.Operations {

		@Override
		public void expireAgent(UUID u) {
			Master.this.heartExpireAgent(u);
		}

		@Override
		public void terminateAgent(UUID u) {
			Master.this.heartTerminateAgent(u);
		}

		@Override
		public void disconnectAgent(UUID u, AgentShutdown.Reason reason, int signal) {
			Master.this.heartDisconnectAgent(u, reason, signal);
		}

		@Override
		public void pingAgent(UUID u) {
			Master.this.heartPingAgent(u);
		}

		@Override
		public Instant getLastHeardFrom(UUID u) {
			return Master.this.heartGetLastHeartFrom(u);
		}

		@Override
		public Instant getExpiryTime(UUID u) {
			return Master.this.heartGetExpiryTime(u);
		}

	}
	// </editor-fold>
}
