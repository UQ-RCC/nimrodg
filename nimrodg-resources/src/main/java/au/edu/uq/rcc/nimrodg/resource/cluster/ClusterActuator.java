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
package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.act.POSIXActuator;
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType.ClusterConfig;
import java.io.IOException;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.UUID;
import au.edu.uq.rcc.nimrodg.api.Resource;

abstract class ClusterActuator<C extends ClusterConfig> extends POSIXActuator<C> {
	protected ClusterActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, C cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
	}
}
