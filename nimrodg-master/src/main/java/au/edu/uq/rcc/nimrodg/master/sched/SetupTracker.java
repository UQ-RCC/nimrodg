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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import java.util.AbstractMap;
import java.util.UUID;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import au.edu.uq.rcc.nimrodg.api.Resource;

public class SetupTracker {

	public static class CapPair extends AbstractMap.SimpleImmutableEntry<Resource, Experiment> {

		public CapPair(Resource key, Experiment value) {
			super(key, value);
		}

		public Resource getNode() {
			return this.getKey();
		}

		public Experiment getExperiment() {
			return this.getValue();
		}
	}

	private final BidiMap<UUID, CapPair> m_JobMap;
	private final BidiMap<UUID, CapPair> m_AgentMap;

	public SetupTracker() {
		m_JobMap = new DualHashBidiMap<>();
		m_AgentMap = new DualHashBidiMap<>();
	}

	public CapPair getJobPair(UUID uuid) {
		return m_JobMap.getOrDefault(uuid, null);
	}

	public boolean isSetupRunning(Resource node, Experiment exp) {
		return m_JobMap.containsValue(new CapPair(node, exp));
	}

	public boolean hasAgentBeenRequested(Resource node, Experiment exp) {
		return m_AgentMap.containsValue(new CapPair(node, exp));
	}

	public void markAgentRequested(UUID agentUuid, Resource node, Experiment exp) {
		CapPair cp = new CapPair(node, exp);

		if(m_AgentMap.containsValue(cp)) {
			return;
		}
		m_AgentMap.put(agentUuid, cp);
	}

	public void markRunning(UUID jobUuid, Resource node, Experiment exp) {
		CapPair cp = new CapPair(node, exp);

		if(m_JobMap.containsValue(cp)) {
			return;
		}

		m_JobMap.putIfAbsent(jobUuid, cp);
	}

	public void markJobDone(UUID jobUuid) {
		CapPair cp = m_JobMap.getOrDefault(jobUuid, null);

		if(cp == null) {
			return;
		}

		m_AgentMap.removeValue(cp);
	}

}
