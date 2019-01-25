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
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import java.util.HashMap;
import java.util.UUID;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

public class ASJob {

	// Agent UUID -> Agent
	private final HashMap<UUID, Agent> m_AgentMappings;

	// NetworkJob UUID <=> JobAttempt
	private final BidiMap<UUID, JobAttempt> m_JobAttemptAttemptJob;

	// Agent <=> Job
	private final BidiMap<UUID, UUID> m_AgentJobJobAgent;

	public ASJob() {
		m_AgentMappings = new HashMap<>();
		m_JobAttemptAttemptJob = new DualHashBidiMap<>();
		m_AgentJobJobAgent = new DualHashBidiMap<>();
	}

	public JobAttempt getJobAttempt(UUID uuid) {
		return m_JobAttemptAttemptJob.get(uuid);
	}

	public UUID getJobUUID(JobAttempt job) {
		return m_JobAttemptAttemptJob.getKey(job);
	}

	public Agent getAgent(UUID uuid) {
		return m_AgentMappings.get(uuid);
	}

	public UUID getJobOnAgent(Agent agent) {
		return getJobOnAgent(agent.getUUID());
	}

	public UUID getJobOnAgent(UUID uuid) {
		return m_AgentJobJobAgent.getKey(uuid);
	}

	public Agent getAgentOnJob(UUID jobUuid) {
		UUID agentUuid = m_AgentJobJobAgent.getKey(jobUuid);
		if(agentUuid == null) {
			return null;
		}

		return getAgent(agentUuid);
	}

	public void registerJobRun(UUID uuid, JobAttempt job, Agent agent) {
		m_JobAttemptAttemptJob.put(uuid, job);
		m_AgentMappings.put(agent.getUUID(), agent);
		m_AgentJobJobAgent.put(agent.getUUID(), uuid);
	}

	public JobAttempt reportAgentFinish(Agent agent) {
		UUID jobUuid = m_AgentJobJobAgent.remove(agent.getUUID());
		return m_JobAttemptAttemptJob.remove(jobUuid);
	}
}
