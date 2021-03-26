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
package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.MachinePair;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.api.setup.AMQPConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.TransferConfigBuilder;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.IniSetupConfig;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.ini4j.Ini;

/**
 * Nimrod/G Setup commands. These aren't given an API instance, they can create it themselves if they need it.
 */
public final class Setup extends DefaultCLICommand {

	private Setup() {

	}

	@Override
	public String getCommand() {
		return "setup";
	}

	@Override
	public int execute(Namespace args, UserConfig config, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		NimrodAPIFactory fact = NimrodCLICommand.createFactory(config);

		switch(args.getString("operation")) {
			case "generate": {

				/* NB: Not using resolveSetupConfiguration() here to keep the formatting. */
				byte[] rawCfg = NimrodUtils.readEmbeddedFile(NimrodCLI.class, "nimrod-setup-defaults.ini");

				String _ini = args.getString("setupini");
				if(_ini.equals("-")) {
					System.out.write(rawCfg);
				} else if(args.getBoolean("force")) {
					Files.write(Paths.get(_ini), rawCfg, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				} else {
					Files.write(Paths.get(_ini), rawCfg, StandardOpenOption.CREATE_NEW);
				}

				System.err.printf("Sample setup configuration written to %s.\n", _ini.equals("-") ? "stdout" : _ini);
				System.err.println("Please edit appropriately and run:");
				System.err.println("  nimrod setup init <path>");
				return 0;
			}
			case "init": {
				Ini ini = resolveSetupConfiguration(
						configDirs,
						Optional.ofNullable(args.getString("setupini")),
						false,
						args.getBoolean("skip_system")
				);

				SetupConfig cfg = IniSetupConfig.parseToBuilder(ini, config.configPath()).build();
				try(NimrodSetupAPI api = fact.getSetupAPI(config)) {
					api.reset();
				}

				try(NimrodAPI api = fact.createNimrod(config)) {
					NimrodUtils.setupApi(api, cfg);
				}
				return 0;
			}
			case "configure": {
				SetupConfig cfg = new SetupConfigBuilder()
						.workDir(args.getString("workdir"))
						.storeDir(args.getString("storedir"))
						.amqp(new AMQPConfigBuilder()
								.uri(URI.create(args.getString("amqp_uri")))
								.routingKey(args.getString("amqp_routing_key"))
								.certPath(args.getString("amqp_cert"))
								.noVerifyPeer(Objects.requireNonNullElse(args.getBoolean("amqp_no_verify_peer"), false))
								.noVerifyHost(Objects.requireNonNullElse(args.getBoolean("amqp_no_verify_host"), false))
								.build())
						.transfer(new TransferConfigBuilder()
								.uri(URI.create(args.getString("tx_uri")))
								.certPath(args.getString("tx_cert"))
								.noVerifyPeer(Objects.requireNonNullElse(args.getBoolean("tx_no_verify_peer"), false))
								.noVerifyHost(Objects.requireNonNullElse(args.getBoolean("tx_no_verify_host"), false))
								.build())
						.build();
				try(NimrodSetupAPI api = fact.getSetupAPI(config)) {
					api.reset();
				}

				try(NimrodAPI api = fact.createNimrod(config)) {
					NimrodUtils.setupApi(api, cfg);
				}
				return 0;
			}
		}

		return 0;
	}

	private static Ini resolveSetupConfiguration(Path[] configDirs, Optional<String> userPath, boolean skipInternal, boolean skipSystem) {
		Ini internalDefaults = new Ini();
		if(!skipInternal) {
			/* Load our internal defaults. */
			try(StringReader r = new StringReader(NimrodUtils.readEmbeddedFileAsString(NimrodCLI.class, "nimrod-setup-defaults.ini"))) {
				internalDefaults.load(r);
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		Stream<Ini> sysInis = Stream.empty();
		if(!skipSystem) {
			/* Load system-wide configuration from each of the configuration dirs. */
			List<Path> confDirs = new ArrayList<>(Arrays.asList(configDirs));
			Collections.reverse(confDirs);
			sysInis = confDirs.stream()
					.map(p -> p.resolve("setup-defaults.ini"))
					.filter(Files::exists)
					.map(p -> {
						try(InputStream is = Files.newInputStream(p)) {
							return new Ini(is);
						} catch(IOException e) {
							throw new UncheckedIOException(e);
						}
					});
		}

		/* Load user-spsecified configuration. */
		Ini userConfig = new Ini();
		userPath.ifPresent(p -> {
			try {
				if(p.equals("-")) {
					userConfig.load(System.in);
				} else {
					Path iniPath = Paths.get(p);
					try(InputStream is = Files.newInputStream(iniPath)) {
						userConfig.load(is);
					}
				}
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		/* Merge the configurations. */
		Ini newIni = new Ini();
		Stream.concat(Stream.concat(Stream.of(internalDefaults), sysInis), Stream.of(userConfig)).forEach(i -> {
			new HashSet<>(i.keySet()).forEach(s -> newIni.merge(s, i.get(s), (a, b) -> {
				new HashSet<>(b.keySet()).forEach(k -> a.merge(k, b.get(k), (aa, bb) -> bb));
				return a;
			}));
		});
		return newIni;
	}

	public static void main(String[] args) throws Exception {
		System.exit(NimrodCLI.cliMain(new String[]{"setup", "init", "/home/zane/.config/nimrod/nimrod-setup.ini"}));
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new Setup(), "Nimrod/G setup functionality.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers()
					.dest("operation");

			{
				Subparser sp = subs.addParser("generate")
						.help("Generate a sample setup configuration file.");

				sp.addArgument("--force", "-f")
						.dest("force")
						.type(Boolean.class)
						.action(Arguments.storeTrue())
						.help("Force creation if already exists.");

				sp.addArgument("setupini");
			}

			{
				Subparser sp = subs.addParser("dbinit")
						.help("(Re)initialise the API Backend Storage.")
						.description(
								"(Re)initialise Backend Storage default configuration.\n"
								+ "WARNING: THIS WILL DESTROY ALL DATA"
						);

				sp.addArgument("--no-defaults", "-nd")
						.dest("no_defaults")
						.type(Boolean.TYPE)
						.action(Arguments.storeTrue())
						.help("Only reinitialise the backend, don't add default configuration.");
			}

			{
				Subparser sp = subs.addParser("init")
						.help("(Re)initialise the Nimrod backend.")
						.description(
								"(Re)initialise the Nimrod backend.\n"
								+ "WARNING: THIS WILL DESTROY ALL DATA"
						);

				sp.addArgument("--skip-system")
						.dest("skip_system")
						.type(Boolean.class)
						.action(Arguments.storeTrue())
						.help("Ignore system-wide configuration");

				sp.addArgument("setupini")
						.setDefault((String)null)
						.nargs("?")
						.help("The user setup configuration. May be omitted.");
			}

			{
				Subparser sp = subs.addParser("configure");
				sp.addArgument("--workdir")
						.dest("workdir")
						.help("Base Working Directory.")
						.required(true);

				sp.addArgument("--storedir")
						.dest("storedir")
						.help("Root File Store.")
						.required(true);

				addPrefixedUriArg(sp, "amqp", "AMQP", true);

				sp.addArgument("--amqp-routing-key")
						.dest("amqp_routing_key")
						.help("AMQP routing key.")
						.required(true);

				addPrefixedUriArg(sp, "tx", "Transfer", true);
			}
		}

	};
}
