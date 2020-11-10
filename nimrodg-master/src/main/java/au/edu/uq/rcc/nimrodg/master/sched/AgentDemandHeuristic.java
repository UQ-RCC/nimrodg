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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.master.ConfigListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentDemandHeuristic implements ConfigListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(AgentDemandHeuristic.class);

	private class HState {

		public HState(int demand) {
			this.demand = demand;
			this.spawning = 0;
		}

		public HState(int demand, int spawning) {
			this(demand);
			this.spawning = spawning;
		}
		/**
		 * Our "demand", or how often we're requested for agents. If we attempt to spawn an agent and it fails, our
		 * demand is penalised heavily.
		 */
		public int demand;

		/**
		 * How many agents are we currently spawning? If > 0, don't try to spawn on this resource again until we're
		 * finished.
		 */
		public int spawning;
	}

	private static final int DEAFULT_LAUNCH_FAILURE_PENALTY = -10;
	private static final int DEFAULT_SPAWN_CAP = 10;

	/*
	 * Each and every ResourceNode in this will have no common children.
	 * i.e. this is as high up in the resource tree as possible.
	 */
	private final Map<Resource, HState> m_Demand;

	private int m_LaunchPenalty;
	private int m_SpawnCap;
	private boolean m_DumpDirty;

	public AgentDemandHeuristic() {
		m_Demand = new HashMap<>();
		m_LaunchPenalty = DEAFULT_LAUNCH_FAILURE_PENALTY;
		m_DumpDirty = true;
	}

	public void setLaunchPenalty(int penalty) {
		m_LaunchPenalty = penalty < 0 ? DEAFULT_LAUNCH_FAILURE_PENALTY : penalty;
	}

	public void setSpawnCap(int cap) {
		m_SpawnCap = cap < 0 ? DEFAULT_SPAWN_CAP : cap;
	}

	public void requestAgentLaunch(Resource node) {

		/* No more than 10 launch requests can be done. */
		if(m_Demand.values().stream().mapToInt(s -> Math.max(0, s.demand)).sum() >= m_SpawnCap) {
			return;
		}

		/* If we're in demand, increase it. */
		if(m_Demand.containsKey(node)) {
			HState s = m_Demand.get(node);
			++s.demand;
			return;
		}

		/* Otherwise, we're new here. Set our demand to 1. */
		m_Demand.put(node, new HState(1));
	}

	public List<UUID> launchAgents(AgentScheduler.Operations ops) {
		if(m_Demand.isEmpty()) {
			return new ArrayList<>();
		}

		// FIXME: There's probably a better data structure I could use for this.
		List<Map.Entry<Resource, HState>> sortedDemands = m_Demand.entrySet().stream().sorted((e1, e2) -> Integer.compare(e1.getValue().demand, e2.getValue().demand)).collect(Collectors.toList());

		/* Get the min/max, skipping anything that's < 0 */
		int min = Math.max(1, sortedDemands.get(0).getValue().demand);
		int max = Math.max(0, sortedDemands.get(sortedDemands.size() - 1).getValue().demand);

		if(max == 0) {
			return new ArrayList<>();
		}

		double ratio = min / (double)max;

		boolean changed = false;
		List<UUID> agents = new ArrayList<>();
		for(Map.Entry<Resource, HState> e : sortedDemands) {
			HState s = e.getValue();

			/* No demand or spawning, ignore it. */
			if(s.demand <= 0 || s.spawning > 0) {
				continue;
			}

			int nAgents = (int)(s.demand / ratio);

			// Handy to see if nAgents has become 2,147,483,647 again -.-
			//System.err.printf("s.demand = %d, ratio = %f, min = %d, max = %d, nAgents = %d\n", s.demand, ratio, min, max, nAgents);
			agents.addAll(List.of(ops.launchAgents(e.getKey(), nAgents)));
			s.spawning += nAgents;
			s.demand -= nAgents;
			changed = true;
		}

		m_DumpDirty = changed;
		return agents;
	}

	public void onAgentLaunchFailure(Resource node) {
		HState s = m_Demand.get(node);

		LOGGER.trace("Resource '{}' failed to launch agent.", node.getName());
		LOGGER.trace("    Demand: {} -> {}", s.demand, s.demand + m_LaunchPenalty);
		LOGGER.trace("  Spawning: {} -> {}", s.spawning, s.spawning - 1);

		s.demand += m_LaunchPenalty;
		--s.spawning;
	}

	public void onAgentLaunchSuccess(Resource node) {
		HState s = m_Demand.get(node);
		LOGGER.trace("Node '{}' successfully launched agent.", node);
		LOGGER.trace("    Demand: {} -> {}", s.demand, s.demand);
		LOGGER.trace("  Spawning: {} -> {}", s.spawning, s.spawning - 1);

		--s.spawning;
	}

	public void dumpStats() {
		if(!m_DumpDirty || !LOGGER.isInfoEnabled()) {
			m_DumpDirty = false;
			return;
		}

		LOGGER.info("Agent Demands:");
		for(Map.Entry<Resource, HState> e : m_Demand.entrySet()) {
			HState s = e.getValue();
			LOGGER.info(String.format("  %16s: Spawning: %d, Demand: %d", e.getKey().getName(), s.spawning, s.demand));
		}

		m_DumpDirty = false;
	}

	@Override
	public void onConfigChange(String key, String oldValue, String newValue) {
		Objects.requireNonNull(key, "key");

		switch(key) {
			case "nimrod.sched.default.launch_penalty":
				m_LaunchPenalty = ConfigListener.get(newValue, m_LaunchPenalty, DEAFULT_LAUNCH_FAILURE_PENALTY, 0, Integer.MAX_VALUE);
				break;

			case "nimrod.sched.default.spawn_cap":
				m_SpawnCap = ConfigListener.get(newValue, m_SpawnCap, DEFAULT_SPAWN_CAP, 0, Integer.MAX_VALUE);
				break;
		}
	}
}
