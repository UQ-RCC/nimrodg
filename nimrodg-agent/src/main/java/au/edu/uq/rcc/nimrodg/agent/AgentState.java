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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;

import java.time.Instant;
import java.util.UUID;
import javax.json.JsonObject;

public interface AgentState extends AgentInfo {

	void setState(State state);

	void setQueue(String q);

	void setUUID(UUID uuid);

	void setShutdownSignal(int s);

	void setShutdownReason(ShutdownReason r);

	void setConnectionTime(Instant time);

	void setLastHeardFrom(Instant time);

	void setExpiryTime(Instant time);

	void setExpired(boolean expired);

	void setSecretKey(String secretKey);

	void setActuatorData(JsonObject data);
}
