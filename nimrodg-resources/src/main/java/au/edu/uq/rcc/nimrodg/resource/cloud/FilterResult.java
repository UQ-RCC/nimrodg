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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import org.jclouds.compute.domain.NodeMetadata;

class FilterResult {

	public final Map<NodeInfo, Set<Actuator.Request>> toLaunch;
	public final Map<NodeMetadata, Set<Actuator.Request>> toFail;
	public final Set<Actuator.Request> leftovers;
	public final Map<Actuator.Request, Integer> indexMap;
	public final Map<Actuator.Request, NodeInfo> uuidMap;

	private FilterResult(Map<NodeInfo, Set<Actuator.Request>> toLaunch, Map<NodeMetadata, Set<Actuator.Request>> toFail, Set<Actuator.Request> leftovers, Map<Actuator.Request, Integer> indexMap, Map<Actuator.Request, NodeInfo> uuidMap) {
		this.toLaunch = toLaunch;
		this.toFail = toFail;
		this.leftovers = leftovers;
		this.indexMap = indexMap;
		this.uuidMap = uuidMap;
	}

	static FilterResult filterRequestsToNodes(Actuator.Request[] requests, Set<NodeInfo> good, Set<NodeMetadata> bad, int agentsPerNode) {
		ArrayDeque<NodeInfo> _good = new ArrayDeque<>(good);
		ArrayDeque<NodeMetadata> _bad = new ArrayDeque<>(bad);

		Map<NodeInfo, Set<Actuator.Request>> toLaunch = new HashMap<>(good.size());
		Map<NodeMetadata, Set<Actuator.Request>> toFail = new HashMap<>(bad.size());
		Map<Actuator.Request, NodeInfo> uuidMap = new HashMap<>(requests.length);

		int i = 0;
		while(!_good.isEmpty()) {
			if(i >= requests.length) {
				break;
			}

			NodeInfo ni = _good.peek();
			Set<Actuator.Request> agents = NimrodUtils.getOrAddLazy(toLaunch, ni, nii -> new HashSet<>(agentsPerNode));
			if(agents.size() >= agentsPerNode) {
				_good.poll();
				continue;
			}

			agents.add(requests[i]);
			uuidMap.put(requests[i], ni);
			++i;
		}

		while(!_bad.isEmpty()) {
			if(i >= requests.length) {
				break;
			}

			NimrodUtils.getOrAddLazy(toFail, _bad.poll(), nn -> new HashSet<>(agentsPerNode)).add(requests[i++]);
		}

		Set<Actuator.Request> leftovers = new HashSet<>(requests.length - i);
		for(int j = i; j < requests.length; ++j) {
			leftovers.add(requests[j]);
		}

		Map<Actuator.Request, Integer> indexMap = new HashMap<>(requests.length);
		for(i = 0; i < requests.length; ++i) {
			indexMap.put(requests[i], i);
		}

		return new FilterResult(toLaunch, toFail, leftovers, indexMap, uuidMap);
	}
}
