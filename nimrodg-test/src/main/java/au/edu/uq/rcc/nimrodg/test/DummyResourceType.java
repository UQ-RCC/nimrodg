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

import au.edu.uq.rcc.nimrodg.api.Actuator;
import java.io.PrintStream;
import javax.json.JsonStructure;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.MasterResourceType;
import java.security.cert.Certificate;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.nio.file.Path;
import java.util.List;

public class DummyResourceType implements MasterResourceType {

	@Override
	public String getName() {
		return "dummy";
	}

	@Override
	public JsonStructure parseCommandArguments(AgentProvider ap, String[] args, PrintStream out, PrintStream err, Path[] configDirs) {
		return JsonStructure.EMPTY_JSON_OBJECT;
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs) {
		return new DummyActuator(ops, node, amqpUri, certs);
	}

	@Override
	public boolean validateConfiguration(AgentProvider ap, JsonStructure cfg, List<String> errors) {
		return true;
	}

}
