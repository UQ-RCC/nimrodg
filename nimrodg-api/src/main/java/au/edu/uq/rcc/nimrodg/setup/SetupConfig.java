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
package au.edu.uq.rcc.nimrodg.setup;

import java.net.URI;
import java.util.Map;

public interface SetupConfig {

	public interface AMQPConfig {

		URI uri();

		String routingKey();

		String certPath();

		boolean noVerifyPeer();

		boolean noVerifyHost();
	}

	public interface TransferConfig {

		URI uri();

		String certPath();

		boolean noVerifyPeer();

		boolean noVerifyHost();
	}

	public interface MachinePair {

		String system();

		String machine();
	}

	String workDir();

	String storeDir();

	AMQPConfig amqp();

	TransferConfig transfer();

	Map<String, String> agents();

	Map<MachinePair, String> agentMappings();

	Map<String, String> resourceTypes();

	Map<String, String> properties();
}
