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

import au.edu.uq.rcc.nimrodg.agent.MessageBackend;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.master.AMQProcessorImpl;
import au.edu.uq.rcc.nimrodg.master.Master;
import au.edu.uq.rcc.nimrodg.master.MessageQueueListener;
import au.edu.uq.rcc.nimrodg.master.sched.DefaultAgentScheduler;
import au.edu.uq.rcc.nimrodg.master.sched.DefaultJobScheduler;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.log4j.LogManager;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MasterCmd extends NimrodCLICommand {

	@Override
	public String getCommand() {
		return "master";
	}

	private static final long TICK_RATE = 500L;

	private static Certificate[] loadCerts(String path) throws CertificateException, IOException {
		if(path == null || path.isEmpty()) {
			return new Certificate[0];
		} else {
			return ActuatorUtils.readX509Certificates(Paths.get(path));
		}
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		String expName = args.getString("exp_name");
		Experiment exp = nimrod.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'\n", expName);
			return 1;
		}

		NimrodConfig cfg = nimrod.getConfig();
		NimrodURI amqpUri = cfg.getAmqpUri();
		Certificate[] certs;
		try {
			certs = loadCerts(amqpUri.certPath);
		} catch(IOException | CertificateException e) {
			e.printStackTrace(err);
			return 1;
		}

		Object monitor = new Object();
		AtomicBoolean hasQuit = new AtomicBoolean(false);

		long tickRate = args.getLong("tick_rate");

		if(!nimrod.getAPICaps().master) {
			err.printf("API Implementation doesn't provide master capabilities.\n");
			return 1;
		}

		String tlsProtocol = nimrod.getProperty("nimrod.master.amqp.tls_protocol").orElse("TLSv1.2");

		try(Master m = new Master((NimrodMasterAPI)nimrod, exp, DefaultJobScheduler.FACTORY, DefaultAgentScheduler.FACTORY)) {
			try(AMQProcessorImpl amqp = new AMQProcessorImpl(
					amqpUri.uri,
					certs,
					tlsProtocol,
					cfg.getAmqpRoutingKey(),
					amqpUri.noVerifyPeer,
					amqpUri.noVerifyHost,
					(msg, body) -> {
						MessageQueueListener.MessageOperation op = m.processAgentMessage(msg, body);
						synchronized(monitor) {
							monitor.notify();
						}
						return op;
					},
					ForkJoinPool.commonPool()
			)) {
				m.setAMQP(amqp);

				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					m.flagStop();
					synchronized(monitor) {
						monitor.notify();
					}

					while(!hasQuit.get()) {
						synchronized(hasQuit) {
							try {
								hasQuit.wait();
							} catch(InterruptedException e) {
								/* nop */
							}
						}
					}

					LogManager.shutdown();
				}));

				while(m.tick()) {
					try {
						synchronized(monitor) {
							monitor.wait(tickRate);
						}
					} catch(InterruptedException e) {
						/* nop */
					}
				}
			}
		} catch(IOException | TimeoutException | URISyntaxException | GeneralSecurityException e) {
			e.printStackTrace(err);
			hasQuit.set(true);
			synchronized(hasQuit) {
				hasQuit.notify();
			}
			return 1;
		}
		hasQuit.set(true);
		synchronized(hasQuit) {
			hasQuit.notify();
		}
		return 0;
	}

	public static void main(String[] args) throws Exception {
		System.exit(NimrodCLI.cliMain(new String[]{"-d", "master", "--tick-rate=100", "exp1"}));
	}
	public static final CommandEntry DEFINITION = new CommandEntry(new MasterCmd(), "Start the experiment master.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);
			addExpNameArg(parser);

			parser.addArgument("--tick-rate")
					.dest("tick_rate")
					.type(Long.class)
					.setDefault(TICK_RATE)
					.help("Tick Rate (ms)");
		}

	};
}
