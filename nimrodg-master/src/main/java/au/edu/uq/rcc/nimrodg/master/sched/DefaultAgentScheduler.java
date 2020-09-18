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
package au.edu.uq.rcc.nimrodg.master.sched;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.agent.messages.NetworkJob;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.MsgUtils;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.master.AgentSchedulerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.master.Master;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAgentScheduler implements AgentScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAgentScheduler.class);

	private Operations ops;

	private final LinkedHashSet<JobAttempt> m_PendingJobs;
	private final FailureTracker m_FailureTracker;
	private final LinkedHashSet<JobAttempt> m_HeldJobs;
	private final ASJob m_AssJob;
	private final AgentDemandHeuristic m_AgentHeuristic;
	private final HashSet<Agent> m_AllAgents;
	private final HashSet<Agent> m_ReadyAgents;
	private final HashSet<UUID> m_LaunchingAgents;
	private final SetupTracker m_Setups;

	int m_LastPendingJobs;
	int m_LastHeldJobs;

	public DefaultAgentScheduler() {
		this.ops = null;
		m_PendingJobs = new LinkedHashSet<>();
		m_FailureTracker = new FailureTracker();
		m_HeldJobs = new LinkedHashSet<>();
		m_AssJob = new ASJob();
		m_AgentHeuristic = new AgentDemandHeuristic();
		m_AllAgents = new HashSet<>();
		m_ReadyAgents = new HashSet<>();
		m_LaunchingAgents = new HashSet<>();
		m_Setups = new SetupTracker();
		m_LastPendingJobs = m_LastHeldJobs = 0;
	}

	@Override
	public void resync(Set<Agent> agents, Set<Master.RunningJob> jobs) {
		m_AllAgents.addAll(agents);
		agents.stream()
				.filter(ag -> ag.getState() == Agent.State.READY)
				.forEach(m_ReadyAgents::add);
		agents.stream()
				.filter(ag -> ag.getState() == Agent.State.WAITING_FOR_HELLO)
				.map(ag -> ag.getUUID())
				.forEach(m_LaunchingAgents::add);

		jobs.stream()
				.filter(rj -> rj.managed)
				.forEach(rj -> {
					m_PendingJobs.remove(rj.att);
					m_HeldJobs.remove(rj.att);
					m_AssJob.registerJobRun(rj.uuid, rj.att, rj.agent);
				});

		/* Handle job setups. */
		jobs.stream()
				.filter(rj -> !rj.managed)
				.forEach(rj -> {
					/* FIXME: getExperiment() */
					Experiment exp = rj.job.getExperiment();
					Resource res = ops.getAgentResource(rj.agent);
					m_Setups.markAgentRequested(rj.agent.getUUID(), res, exp);
					m_Setups.markRunning(rj.uuid, res, exp);
				});
	}

	@Override
	public void setAgentOperations(Operations ops) throws IllegalArgumentException {
		if(ops == null || this.ops != null) {
			throw new IllegalArgumentException();
		}
		this.ops = ops;
	}

	@Override
	public void onAgentStateUpdate(Agent agent, Resource node, Agent.State oldState, Agent.State newState) {
		LOGGER.trace("onAgentStateUpdate({}, {}, {}, {})", agent.getUUID(), node.getPath(), oldState, newState);

		boolean scheduleNext = false;
		if(oldState == null) {
			assert newState == Agent.State.WAITING_FOR_HELLO;
			m_AllAgents.add(agent);
		} else if(oldState == Agent.State.WAITING_FOR_HELLO && newState == Agent.State.READY) {
			/* WAITING_FOR_HELLO -> READY, we can start doing things. */
			scheduleNext = true;
			m_AgentHeuristic.onAgentLaunchSuccess(node);
			m_LaunchingAgents.remove(agent.getUUID());
		} else if(oldState == Agent.State.READY) {
			m_ReadyAgents.remove(agent);

			if(newState == Agent.State.SHUTDOWN) {
				/* READY->SHUTDOWN, we died. */
				m_AllAgents.remove(agent);
			}
		} else if(oldState == Agent.State.BUSY) {

			JobAttempt att = m_AssJob.reportAgentFinish(agent);
			if(newState == Agent.State.READY) {
				/* BUSY->READY, we're done (for our purposes anyway, this might have been a cancel). */
				scheduleNext = true;
			} else if(newState == Agent.State.SHUTDOWN) {
				/* BUSY->SHUTDOWN, we died. */
				m_AllAgents.remove(agent);
				ops.reportJobFailure(att, agent, Operations.FailureReason.CRASHED);
			}
		}

		if(scheduleNext) {
			m_ReadyAgents.add(agent);
		}
	}

	private void releaseJob(JobAttempt att, Agent agent) {
		m_AssJob.registerJobRun(ops.runJob(att, agent), att, agent);
	}

	/*
	 * TODO: onAgentLaunchFailure() and onAgentExpiry() could possibly be merged.
	 *  Since agents are now tracked even when in WAITING_FOR_HELLO, perhaps this logic \
	 *  could even be moved into onAgentStateUpdate()
	 */

	@Override
	public void onAgentLaunchFailure(Agent agent, Resource node, Throwable t) {
		LOGGER.trace("Agent launch failure for '{}' on '{}'.", agent.getUUID(), node.getPath());
		if(t instanceof NimrodException.ResourceFull) {
			LOGGER.trace("  Resource full...");
		} else {
			m_FailureTracker.reportLaunchFailure(agent.getUUID(), node, t);
		}

		m_AgentHeuristic.onAgentLaunchFailure(node);
		m_LaunchingAgents.remove(agent.getUUID());
		m_AllAgents.remove(agent);
	}

	@Override
	public void onAgentExpiry(Agent ag, Resource node) {
		LOGGER.trace("Agent {} expired.", ag.getUUID());

		m_ReadyAgents.remove(ag);
		m_AllAgents.remove(ag);
		if(m_LaunchingAgents.remove(ag.getUUID())) {
			m_AgentHeuristic.onAgentLaunchFailure(node);
		}

		JobAttempt att = m_AssJob.reportAgentFinish(ag);
		if(att != null) {
			ops.reportJobFailure(att, ag, Operations.FailureReason.EXPIRED);
		}
	}

	@Override
	public void onJobLaunchFailure(JobAttempt att, NetworkJob job, Agent agent, boolean hardFailure, Throwable t) {
		LOGGER.info("Job '{}' failed to launch on agent '{}'", job.uuid, agent.getUUID());
		m_AssJob.reportAgentFinish(agent);

		if(!hardFailure) {
			LOGGER.info("  Failure is soft, rescheduling...");
			m_PendingJobs.add(att);
		}

		if(t != null) {
			LOGGER.error("Job '{}' failed to launch on agent '{}'", job.uuid, agent.getUUID(), t);
		}
	}

	@Override
	public void onJobRun(JobAttempt att) {
		//LOGGER.trace("onJobRun({})", att.getJob().getPath());
		m_PendingJobs.add(att);
	}

	@Override
	public void onJobCancel(JobAttempt att) {
		LOGGER.trace("onJobCancel({})", att.getJob().getPath());
		m_PendingJobs.remove(att);
		m_HeldJobs.remove(att);
	}

	public static boolean didAgentFail(NetworkJob job, AgentUpdate au) {
		AgentUpdate.CommandResult_ cr = au.getCommandResult();

		if(au.getAction() == AgentUpdate.Action.Continue) {
			return true;
		}

		if(cr.index < job.numCommands - 1) {
			/* Premature failure. */
			return false;
		}

		return cr.status == CommandResult.CommandResultStatus.SUCCESS;
	}

	@Override
	public void onUnmanagedJobUpdate(NetworkJob job, AgentUpdate au, Agent agent) {
		/* This is the result of a setup. Handle it as such. */
		SetupTracker.CapPair cp = m_Setups.getJobPair(job.uuid);
		if(cp == null) {
			LOGGER.warn("Got unmanaged update for unknown job '{}'", job.uuid);
			return;
		}

		boolean bad = didAgentFail(job, au);
		if(bad) {
			/* TODO: What should be the policy here? Unassign the resource? Retry? Currently it'll retry indefinitely. */
			m_Setups.markJobDone(job.uuid);
		} else {
			/* All good, we're now capable! */
			ops.addResourceCaps(cp.getNode(), cp.getExperiment());
			m_Setups.markJobDone(job.uuid);
		}
	}

	/**
	 * Given an agent, get the next held job that this agent is best-suited for. and remove it from the queue.
	 *
	 * @param agent The agent.
	 * @return A job that the agent is best-suited for, or null if there is none.
	 */
	private JobAttempt removeNextHeldJobBestSuitedForAgent(Agent agent) {
		Resource node = ops.getAgentResource(agent);

		JobAttempt att = null;
		for(JobAttempt a : m_HeldJobs) {
			Experiment exp = a.getJob().getExperiment();

			/* If we're capable of this experiment and assigned, we're done here. */
			if(ops.isResourceCapable(node, exp) && ops.getAssignedResources(exp).contains(node)) {
				att = a;
				break;
			}
		}

		m_HeldJobs.remove(att);
		return att;
	}

	@Override
	public boolean tick() {
		Map<Experiment, List<JobAttempt>> expMap = NimrodUtils.mapToParent(m_PendingJobs, j -> j.getJob().getExperiment());

		/* Filter the capable and incapable nodes for each run. Ignore setting-up nodes. */
		Map<Experiment, List<Resource>> capMap = new HashMap<>();
		Map<Experiment, List<Resource>> incapMap = new HashMap<>();
		List<Experiment> unassigned = new ArrayList<>();
		filterCapable(ops, expMap, capMap, incapMap, unassigned);

		/* For incapable resources, try to make them capable. */
		{
			/* We have an optimised list of what to actually do. Now do it. */
			for(Map.Entry<Experiment, List<Resource>> e : incapMap.entrySet()) {
				Experiment exp = e.getKey();
				Task nodestart = exp.getTask(Task.Name.NodeStart);

				for(Resource n : e.getValue()) {
					/* If there's no nodestart, we're automatically capable. */
					if(nodestart == null) {
						ops.addResourceCaps(n, exp);
						continue;
					}

					/* We've got a setup in progress, don't do anything. */
					if(m_Setups.isSetupRunning(n, exp)) {
						continue;
					}

					List<Agent> agents = ops.getResourceAgents(n);
					if(agents.isEmpty()) {
						/* Only attempt to spawn an agent if one hasn't already been requested. */
						if(!m_Setups.hasAgentBeenRequested(n, exp)) {
							m_Setups.markAgentRequested(ops.launchAgents(n, 1)[0], n, exp);
						}
					} else {
						Optional<Agent> ag = agents.stream().filter(a -> a.getState() == Agent.State.READY).findFirst();

						if(!ag.isPresent()) {
							/* No available agent, ignore for now */
							continue;
						}

						/* Resolve the job and run it. */
						UUID nsUuid = UUID.randomUUID();
						/* FIXME: Handle cert path, and other stuff. */
						Optional<NimrodURI> txUri = ops.resolveTransferUri(n, exp);
						NetworkJob nj = MsgUtils.resolveNonSubstitutionTask(nsUuid, nodestart, txUri.map(u -> u.uri).get());
						ops.runUnmanagedJob(nj, ag.get());
						m_Setups.markRunning(nsUuid, n, exp);
					}
				}
			}

		}

		schedulePending(expMap, capMap);

		m_AgentHeuristic.dumpStats();
		{
			if(m_PendingJobs.size() != m_LastPendingJobs || m_HeldJobs.size() != m_LastHeldJobs) {
				LOGGER.trace("{} pending jobs", m_PendingJobs.size());
				LOGGER.trace("{} held jobs", m_HeldJobs.size());
			}
			m_LastPendingJobs = m_PendingJobs.size();
			m_LastHeldJobs = m_HeldJobs.size();
		}
		m_LaunchingAgents.addAll(m_AgentHeuristic.launchAgents(ops));

		/* If we have ready agents, held jobs, and no pending jobs, see if we can release some. */
		if(m_PendingJobs.isEmpty() && !m_HeldJobs.isEmpty()) {
			m_ReadyAgents.forEach(ag -> {
				JobAttempt att = removeNextHeldJobBestSuitedForAgent(ag);
				if(att != null) {
					releaseJob(att, ag);
				}
			});
		}

		/* If we have held jobs, no agents, and no pending agents, move all held jobs into pending. */
		if(!m_HeldJobs.isEmpty() && m_AllAgents.isEmpty() && m_LaunchingAgents.isEmpty()) {
			m_PendingJobs.addAll(m_HeldJobs);
			m_HeldJobs.clear();
		}

		return true;
	}

	private void schedulePending(Map<Experiment, List<JobAttempt>> runMap, Map<Experiment, List<Resource>> capMap) {
		/* For capable resources, start scheduling jobs. */
		for(Map.Entry<Experiment, List<Resource>> e : capMap.entrySet()) {

			Experiment exp = e.getKey();
			List<JobAttempt> jobs = runMap.getOrDefault(exp, null);
			if(jobs == null) {
				continue;
			}

			Set<Resource> ddddd = new HashSet<>(e.getValue());
			m_FailureTracker.applyThreshold(ddddd);

			/* Use our dodgy little heuristic to assign a node to a job */
			Map<JobAttempt, Resource> resMap = assignNodesByWeight(runMap.get(exp), ddddd);


			/* Get the ready agents for each */
			Map<Resource, List<Agent>> agentMap = resMap.values()
					.stream()
					.collect(Collectors.toMap(n -> n, n -> this.getReadyAgents(n), (al1, al2) -> al1));

			/*
			 * For each job, take an agent from its resource and remove that resource from the pool.
			 * If there's no agents left, skip the job.
			 *
			 * We're doing this as there may be multiple jobs per resource.
			 */
			for(Map.Entry<JobAttempt, Resource> ee : resMap.entrySet()) {
				List<Agent> agents = agentMap.get(ee.getValue());
				Agent ag = NimrodUtils.selectRandomFromContainer(agents);
				m_PendingJobs.remove(ee.getKey());
				m_HeldJobs.add(ee.getKey());
				if(ag == null) {
					requestAgentLaunch(ee.getValue());
					continue;
				}
				agents.remove(ag);
			}
		}
	}

	private void requestAgentLaunch(Resource node) {
		m_AgentHeuristic.requestAgentLaunch(node);
	}

	private Map<JobAttempt, Resource> assignNodesByWeight(Collection<JobAttempt> jobs, Collection<Resource> nodes) {
		if(jobs.isEmpty() || nodes.isEmpty()) {
			return new HashMap<>();
		}

		Map<JobAttempt, Resource> map = new HashMap<>();

		/* Weight each resource by the number of times chosen */
		Map<Resource, Float> weights = new HashMap<>();
		nodes.forEach(n -> weights.put(n, 1.0f));

		/* Amount to decrease each time a node is chosen. */
		final float delta = 1.0f / jobs.size();

		for(JobAttempt att : jobs) {
			/* Get the node with the highest weight. Nodes with the same weight are weighted (heh) equally. */
			Map.Entry<Resource, Float> weightEntry = weights.entrySet()
					.stream()
					.sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
					.findFirst()
					.get();

			Resource res = weightEntry.getKey();

			/* Update the weight */
			weightEntry.setValue(weightEntry.getValue() - delta);
			map.put(att, res);
		}

		return map;
	}

	/**
	 * Filter the capable and incapable nodes for each run, ignoring setting-up nodes.
	 *
	 * @param expMap The mapping of Experiments to Runs that have been requested to be scheduled.
	 * @param capMap A mapping of each run to resource nodes that are capable.
	 * @param incapMap A mapping of each run to resource nodes that are incapable.
	 * @param unassigned The list of experiments without any assigned resources.
	 */
	private static void filterCapable(Operations ops, Map<Experiment, List<JobAttempt>> expMap, Map<Experiment, List<Resource>> capMap, Map<Experiment, List<Resource>> incapMap, List<Experiment> unassigned) {

		for(Experiment exp : expMap.keySet()) {
			Collection<Resource> ass = ops.getAssignedResources(exp);
			/* If nothing's assigned, ignore the experiment. */
			if(ass.isEmpty()) {
				//LOGGER.warn("Experiment '{}' is started, but has no assigned resources. Ignoring...", exp);
				unassigned.add(exp);
				continue;
			}

			List<Resource> capable = new ArrayList<>();
			List<Resource> incapable = new ArrayList<>();
			for(Resource r : ass) {
				if(ops.isResourceCapable(r, exp)) {
					capable.add(r);
				} else {
					incapable.add(r);
				}

				capMap.put(exp, capable);
				incapMap.put(exp, incapable);
			}
		}
	}

	private List<Agent> getReadyAgents(Resource node) {
		return ops.getResourceAgents(node).stream().filter(a -> a.getState() == Agent.State.READY).collect(Collectors.toList());
	}

	@Override
	public void onConfigChange(String key, String oldValue, String newValue) {
		m_AgentHeuristic.onConfigChange(key, oldValue, newValue);
	}

	public static final AgentSchedulerFactory FACTORY = () -> new DefaultAgentScheduler();
}
