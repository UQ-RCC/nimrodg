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

import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.ReferenceAgent;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import java.util.Objects;
import java.util.UUID;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class AgentInfo {

	public final UUID uuid;
	public final Resource resource;
	public final CompletableFuture<Actuator> actuator;
	public final ReferenceAgent instance;
	public final DefaultAgentState state;

	public AgentInfo(UUID uuid, Resource resource, Optional<Actuator> actuator, ReferenceAgent instance, DefaultAgentState state) {
		this.uuid = uuid;
		this.resource = resource;
		this.actuator = new CompletableFuture<>();
		actuator.ifPresent(act -> this.actuator.complete(act));
		this.instance = instance;
		this.state = state;
	}

	@Override
	public int hashCode() {
		return uuid.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		if(obj == null) {
			return false;
		}
		if(getClass() != obj.getClass()) {
			return false;
		}
		final AgentInfo other = (AgentInfo)obj;
		return Objects.equals(this.uuid, other.uuid);
	}

}
