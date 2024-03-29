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

import au.edu.uq.rcc.nimrodg.cli.commands.AgentCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.JobCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.PropertyCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.ResourceTypeCmd;
import au.edu.uq.rcc.nimrodg.utils.AppDirs;
import au.edu.uq.rcc.nimrodg.cli.commands.Compile;
import au.edu.uq.rcc.nimrodg.cli.commands.ExperimentCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.GenConfig;
import au.edu.uq.rcc.nimrodg.cli.commands.MasterCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.ResourceCmd;
import au.edu.uq.rcc.nimrodg.cli.commands.Setup;
import au.edu.uq.rcc.nimrodg.cli.commands.Staging;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.log4j.LogManager;

public class NimrodCLI {

	//private static final Logger LOGGER = LogManager.getLogger(NimrodCLI.class);
	private static final CommandEntry[] CLI_COMMANDS = new CommandEntry[]{
			PropertyCmd.DEFINITION,
			ExperimentCmd.DEFINITION,
			MasterCmd.DEFINITION,
			ResourceCmd.DEFINITION,
			ResourceTypeCmd.DEFINITION,
			JobCmd.DEFINITION,
			Setup.DEFINITION,
			Compile.DEFINITION,
			GenConfig.DEFINITION,
			AgentCmd.DEFINITION,
			Staging.DEFINITION
	};

	private static ArgumentParser buildParser(Map<String, CLICommand> commands, Path defaultConfig) {
		ArgumentParser argparser = ArgumentParsers.newArgumentParser("nimrod")
				.defaultHelp(true)
				.description("Invoke Nimrod/G CLI commands");
		argparser.addArgument("-c", "--config")
				.setDefault(defaultConfig.toString())
				.help("Path to configuration file.");

		argparser.addArgument("-d", "--debug")
				.type(Boolean.class)
				.action(Arguments.storeTrue())
				.help("Enable debug output.");

		Subparsers subparsers = argparser.addSubparsers()
				.title("valid commands")
				.dest("command")
				.metavar("command");

		for(CommandEntry cmd : CLI_COMMANDS) {
			Subparser sp = subparsers.addParser(cmd.command.getCommand())
					.help(cmd.help)
					.description(cmd.description);

			cmd.addArgs(sp);
			commands.put(cmd.command.getCommand(), cmd.command);
		}

		return argparser;
	}

	public static int cliMain(String[] args) {
		System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

		Path userHome = AppDirs.INSTANCE.configHome;
		Map<String, CLICommand> commands = new HashMap<>();
		ArgumentParser parser = buildParser(commands, userHome.resolve("nimrod.ini"));
		Namespace ns;
		try {
			ns = parser.parseArgs(args);
		} catch(ArgumentParserException e) {
			parser.handleError(e);
			return 2;
		}

		//System.setProperty("log4j2.debug", "true");
		URL l4jurl;
		if(ns.getBoolean("debug")) {
			l4jurl = NimrodCLI.class.getResource("log4j2-verbose.xml");
		} else {
			l4jurl = NimrodCLI.class.getResource("log4j2-default.xml");
		}

		if(l4jurl != null) {
			System.setProperty("log4j.configurationFile", l4jurl.toString());
		}

		//LOGGER.trace(ns);
		try {
			return commands.get(ns.getString("command")).execute(
					ns,
					System.out,
					System.err,
					Stream.concat(Stream.of(userHome), AppDirs.INSTANCE.configDirs.stream()).toArray(Path[]::new)
			);
		} catch(Throwable t) {
			t.printStackTrace(System.err);
			return 1;
		} finally {
			LogManager.shutdown();
		}
	}

	public static void main(String[] args) throws Exception {
		//System.exit(cliMain(new String[]{"master"}));
		//System.exit(cliMain(new String[]{"setup", "bareinit"}));
		//System.exit(cliMain(new String[]{"setup", "init", "/home/zane/.nimrod/nimrod-setup.ini"}));
		//System.exit(cliMain(new String[]{"resource", "add-root", "tinaroo", "--type=pbs", "--", "--limit=10", "--hostkey=autodetect", "--key=E:/cygwin64/home/zane/.ssh/uqzvanim-tinaroo.pem", "--uri=ssh://uqzvanim@tinaroo.rcc.uq.edu.au", "--", "-A", "UQ-RCC", "-l", "walltime=168:00:00"}));
		//System.exit(cliMain(new String[]{"resource", "add-child", "tinaroo/asedf", "--", "--limit=10", "--", "-A", "UQ-RCC", "-l", "walltime=168:00:00"}));
		System.exit(cliMain(args));
	}

}
