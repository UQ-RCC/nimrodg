/*
 * Nimrod Portal Backend
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
package au.edu.uq.rcc.nimrodg.portal;

import net.sourceforge.argparse4j.inf.Namespace;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class RmqCli {
	private final Environment env;
	private final RestTemplate restTemplate;

	private RmqCli(Environment env) {
		this.env = Objects.requireNonNull(env, "env");
		this.restTemplate = new RestTemplate();
	}

	public int run(Namespace args, PrintStream out, PrintStream err) {
		Objects.requireNonNull(args, "args");

		if(!"init".equals(args.getString("rmqop"))) {
			throw new IllegalStateException();
		}

		/*
		 * This is mostly useful for use with the RabbitMQ Kubernetes operator.
		 * It puts the initial admin credentials in a secret, which is a right pain to get
		 * when using Terraform. This can run as an init container, and create the user account
		 * in the config file, using the secret as a volume mount.
		 */
		String username;
		String password;

		try {
			username = Files.readString(Paths.get(args.getString("userfile"))).strip();
			password = Files.readString(Paths.get(args.getString("passfile"))).strip();
		} catch(IOException e) {
			e.printStackTrace(err);
			return 1;
		}

		RabbitManagementClient rmq = new RabbitManagementClient(
				restTemplate,
				URI.create(env.getRequiredProperty("nimrod.rabbitmq.api")),
				username,
				password
		);

		rmq.addUser(
				env.getRequiredProperty("nimrod.rabbitmq.user"),
				env.getRequiredProperty("nimrod.rabbitmq.password"),
				"administrator");

		return 0;
	}

	public static RmqCli withEnvironment(Environment env) { return new RmqCli(env); }
}
