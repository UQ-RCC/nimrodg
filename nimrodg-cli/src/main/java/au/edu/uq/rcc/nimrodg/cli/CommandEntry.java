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
package au.edu.uq.rcc.nimrodg.cli;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.Subparser;

public class CommandEntry {

	public CommandEntry(CLICommand command, String help) {
		this(command, help, "");
	}

	public CommandEntry(CLICommand command, String help, String desc) {
		this.help = help;
		this.description = desc;
		this.command = command;
	}

	public final CLICommand command;
	public final String help;
	public final String description;

	public void addArgs(Subparser parser) {

	}

	protected static Argument addExpNameArg(Subparser parser) {
		return parser.addArgument("exp_name")
				.type(String.class)
				.help("The name of the experiment.");
	}

	protected static Argument addResNameMultipleArg(Subparser sp) {
		return sp.addArgument("resource_name")
				.type(String.class)
				.help("The name of the resource")
				.nargs("+")
				.required(true);
	}

	protected static Argument addResNameArg(Subparser sp) {
		return sp.addArgument("resource_name")
				.type(String.class)
				.help("The name of the resource")
				.required(true);
	}


	protected final void addPrefixedUriArg(Subparser parser, String prefix, String name, boolean required) {
		parser.addArgument(String.format("--%s-uri", prefix))
				.dest(String.format("%s_uri", prefix))
				.help(String.format("%s URI.", name))
				.required(required);

		parser.addArgument(String.format("--%s-cert", prefix))
				.dest(String.format("%s_cert", prefix))
				.help(String.format("Path to PEM-encoded %s certificate.", name));

		parser.addArgument(String.format("--%s-no-verify-peer", prefix))
				.dest(String.format("%s_no_verify_peer", prefix))
				.help(String.format("Disable %s peer verification.", name))
				.type(Boolean.class);

		parser.addArgument(String.format("--%s-no-verify-host", prefix))
				.dest(String.format("%s_no_verify_host", prefix))
				.help(String.format("Disable %s hostname verification.", name))
				.type(Boolean.class);
	}
}
