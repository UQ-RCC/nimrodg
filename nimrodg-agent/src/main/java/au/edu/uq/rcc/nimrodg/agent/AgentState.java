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
package au.edu.uq.rcc.nimrodg.agent;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import java.time.Instant;
import java.util.UUID;

public interface AgentState {

	public Agent.State getState();

	public void setState(Agent.State state);

	public String getQueue();

	public void setQueue(String q);

	public UUID getUUID();

	public void setUUID(UUID uuid);

	public int getShutdownSignal();

	public void setShutdownSignal(int s);

	public AgentShutdown.Reason getShutdownReason();

	public void setShutdownReason(AgentShutdown.Reason r);

	public Instant getLastHeardFrom();

	public void setLastHeardFrom(Instant time);

	public Instant getCreationTime();

	public void setCreationTime(Instant time);

	/**
	 * Get the Unix timestamp at which this agent should be expired.
	 *
	 * @return The Unix timestamp at which this agent should be expired. If no expiry, returns {@link Instant.MAX}
	 */
	public Instant getExpiryTime();

	public void setExpiryTime(Instant time);

	public boolean getExpired();

	public void setExpired(boolean expired);

}
