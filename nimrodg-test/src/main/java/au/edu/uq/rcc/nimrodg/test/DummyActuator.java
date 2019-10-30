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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentHello;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.Arrays;

public class DummyActuator implements Actuator {

	private final Actuator.Operations ops;
	private final Resource resource;
	private final ArrayList<UUID> pendingAgents;
	private boolean closed;

	public DummyActuator(Actuator.Operations ops, Resource resource) {
		this.ops = ops;
		this.resource = resource;
		this.pendingAgents = new ArrayList<>();
		this.closed = false;
	}

	@Override
	public Resource getResource() throws NimrodAPIException {
		return resource;
	}

	@Override
	public LaunchResult[] launchAgents(UUID[] uuid) {
		if(closed) {
			LaunchResult[] lrs = new LaunchResult[uuid.length];
			Arrays.fill(lrs, new LaunchResult(null, new IllegalStateException("actuator closed")));
			return lrs;
		}
		LaunchResult[] lr = new LaunchResult[uuid.length];
		for(int i = 0; i < lr.length; ++i) {
			pendingAgents.add(uuid[i]);
			lr[i] = new LaunchResult(resource, null);
		}

		return lr;
	}

	public List<AgentHello> simulateHellos() {
		return pendingAgents.stream().map(uuid -> new AgentHello(uuid, UUID.randomUUID().toString())).collect(Collectors.toList());
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public void close() {
		this.closed = true;
	}

	@Override
	public void notifyAgentConnection(AgentState state) {

	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {

	}

	@Override
	public void forceTerminateAgent(UUID[] uuids) {

	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		return !closed;
	}

	@Override
	public AdoptStatus adopt(AgentState state) {
		return closed ? AdoptStatus.Rejected : AdoptStatus.Adopted;
	}
}
