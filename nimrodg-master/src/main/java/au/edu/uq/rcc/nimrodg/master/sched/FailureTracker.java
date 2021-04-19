/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import au.edu.uq.rcc.nimrodg.api.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailureTracker {

	private static final Logger LOGGER = LoggerFactory.getLogger(FailureTracker.class);
	private final Map<Resource, Integer> m_FailureCounts;

	private final int m_Threshold;

	public FailureTracker() {
		m_FailureCounts = new HashMap<>();
		m_Threshold = 3;

	}

	public void reportLaunchFailure(UUID uuid, Resource node, Throwable t) {
		Integer f = m_FailureCounts.getOrDefault(node, 0);
		int val = f + 1;
		m_FailureCounts.put(node, val);
		LOGGER.error(String.format("Failed to launch job on '%s', attempt %s", node.getName(), val), t);
		if(val >= m_Threshold) {
			LOGGER.error("This resource has been blacklisted, fix the error and re-assign to reset.");
		}


	}

	public void applyThreshold(Set<Resource> c) {
		Set<Resource> tmp = new HashSet<>(c);

		for(Resource n : tmp) {
			if(m_FailureCounts.getOrDefault(n, 0) >= m_Threshold) {
				c.remove(n);
			}
		}
	}
}
