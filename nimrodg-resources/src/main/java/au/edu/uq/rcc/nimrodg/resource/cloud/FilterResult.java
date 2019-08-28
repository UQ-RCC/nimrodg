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
package au.edu.uq.rcc.nimrodg.resource.cloud;

import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jclouds.compute.domain.NodeMetadata;

class FilterResult {

	public final Map<NodeInfo, Set<UUID>> toLaunch;
	public final Map<NodeMetadata, Set<UUID>> toFail;
	public final Set<UUID> leftovers;
	public final Map<UUID, Integer> indexMap;
	public final Map<UUID, NodeInfo> uuidMap;

	public FilterResult(Map<NodeInfo, Set<UUID>> toLaunch, Map<NodeMetadata, Set<UUID>> toFail, Set<UUID> leftovers, Map<UUID, Integer> indexMap, Map<UUID, NodeInfo> uuidMap) {
		this.toLaunch = toLaunch;
		this.toFail = toFail;
		this.leftovers = leftovers;
		this.indexMap = indexMap;
		this.uuidMap = uuidMap;
	}

	static FilterResult filterAgentsToNodes(UUID[] uuids, Set<NodeInfo> good, Set<NodeMetadata> bad, int agentsPerNode) {
		ArrayDeque<NodeInfo> _good = new ArrayDeque<>(good);
		ArrayDeque<NodeMetadata> _bad = new ArrayDeque<>(bad);

		Map<NodeInfo, Set<UUID>> toLaunch = new HashMap<>(good.size());
		Map<NodeMetadata, Set<UUID>> toFail = new HashMap<>(bad.size());
		Map<UUID, NodeInfo> uuidMap = new HashMap<>(uuids.length);

		int i = 0;
		while(!_good.isEmpty()) {
			if(i >= uuids.length) {
				break;
			}

			NodeInfo ni = _good.peek();
			Set<UUID> agents = NimrodUtils.getOrAddLazy(toLaunch, ni, nii -> new HashSet<>(agentsPerNode));
			if(agents.size() >= agentsPerNode) {
				_good.poll();
				continue;
			}

			agents.add(uuids[i]);
			uuidMap.put(uuids[i], ni);
			++i;
		}

		while(!_bad.isEmpty()) {
			if(i >= uuids.length) {
				break;
			}

			NimrodUtils.getOrAddLazy(toFail, _bad.poll(), nn -> new HashSet<>(agentsPerNode)).add(uuids[i++]);
		}

		Set<UUID> leftovers = new HashSet<>(uuids.length - i);
		for(int j = i; j < uuids.length; ++j) {
			leftovers.add(uuids[j]);
		}

		Map<UUID, Integer> indexMap = new HashMap<>(uuids.length);
		for(i = 0; i < uuids.length; ++i) {
			indexMap.put(uuids[i], i);
		}

		return new FilterResult(toLaunch, toFail, leftovers, indexMap, uuidMap);
	}
}
