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
import javax.json.JsonObject;

public interface AgentState {

	Agent.State getState();

	void setState(Agent.State state);

	String getQueue();

	void setQueue(String q);

	UUID getUUID();

	void setUUID(UUID uuid);

	int getShutdownSignal();

	void setShutdownSignal(int s);

	AgentShutdown.Reason getShutdownReason();

	void setShutdownReason(AgentShutdown.Reason r);

	Instant getCreationTime();

	// The time at which the state changed from WAITING_FOR_HELLO to READY
	Instant getConnectionTime();

	void setConnectionTime(Instant time);

	Instant getLastHeardFrom();

	void setLastHeardFrom(Instant time);

	/**
	 * Get the Unix timestamp at which this agent should be expired.
	 *
	 * @return The Unix timestamp at which this agent should be expired. If no expiry, returns {@link Instant#MAX}
	 */
	Instant getExpiryTime();

	void setExpiryTime(Instant time);

	boolean getExpired();

	void setExpired(boolean expired);

	String getSecretKey();

	void setSecretKey(String secretKey);

	JsonObject getActuatorData();

	void setActuatorData(JsonObject data);
}
