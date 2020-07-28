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
package au.edu.uq.rcc.nimrodg.resource.act;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType.SSHConfig;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public abstract class POSIXActuator<C extends SSHConfig> implements Actuator {

	private static final Logger LOGGER = LoggerFactory.getLogger(POSIXActuator.class);



	protected final Operations ops;
	protected final Resource node;
	protected final NimrodURI uri;
	protected final String routingKey;
	protected final Certificate[] certs;
	public final C config;
	protected final String nimrodHomeDir;
	protected final String remoteAgentPath;
	protected final Optional<String> remoteCertPath;
	protected final Map<String, String> remoteEnvironment;

	protected boolean isClosed;

	public static final EnumSet<PosixFilePermission> O755 = EnumSet.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE,
			PosixFilePermission.GROUP_READ,
			PosixFilePermission.GROUP_EXECUTE,
			PosixFilePermission.OTHERS_READ,
			PosixFilePermission.OTHERS_EXECUTE
	);

	public static final EnumSet<PosixFilePermission> O644 = EnumSet.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.GROUP_READ,
			PosixFilePermission.OTHERS_READ
	);

	public static final EnumSet<PosixFilePermission> O600 = EnumSet.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE
	);

	protected POSIXActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, C config) throws IOException {
		this.ops = ops;
		this.config = config;
		this.node = node;
		this.uri = amqpUri;
		this.routingKey = ops.getConfig().getAmqpRoutingKey();
		this.certs = Arrays.copyOf(certs, certs.length);

		CleanupStage cs = CleanupStage.Client;
		try(RemoteShell client = makeClient()) {
			try {
				Path agentPath = config.agentInfo.getAgentPath();

				remoteEnvironment = client.getEnvironment();

				String homeDir = remoteEnvironment.get("HOME");
				if(homeDir == null) {
					throw new IOException("Error retrieving home directory");
				}

				nimrodHomeDir = ActuatorUtils.posixJoinPaths(
						homeDir, ".nimrod",
						ActuatorUtils.buildUniqueString(this)
				);

				SshdClient.CommandResult mkdir = client.runCommand("mkdir", "-p", nimrodHomeDir);
				if(mkdir.status != 0) {
					throw new IOException("Error creating working directory");
				}

				cs = CleanupStage.TmpDir;

				remoteAgentPath = ActuatorUtils.posixJoinPaths(nimrodHomeDir, agentPath.getName(agentPath.getNameCount() - 1).toString());

				Instant now = Instant.now();
				client.upload(remoteAgentPath, agentPath, O755, now);
				cs = CleanupStage.SCP;

				if(certs.length > 0) {
					this.remoteCertPath = Optional.of(ActuatorUtils.posixJoinPaths(nimrodHomeDir, "cacert.pem"));
					byte[] pemCerts = ActuatorUtils.writeCertificatesToPEM(certs);
					client.upload(remoteCertPath.get(), pemCerts, O644, now);
				} else {
					this.remoteCertPath = Optional.empty();
				}
			} catch(IOException e) {
				doCleanup(client, cs);
				throw e;
			}
		}

		this.isClosed = false;
	}

	public void nop() throws IOException {
		try(RemoteShell client = makeClient()) {
			client.runCommand(":");
		}
	}

	private enum CleanupStage {
		Client,
		TmpDir,
		SCP,
		Max
	}

	protected final RemoteShell makeClient() throws IOException {
		return config.transportFactory.create(config.transportConfig, Paths.get(ops.getConfig().getWorkDir()));
	}

	/* 'Cause Java doesn't have goto */
	private void doCleanup(RemoteShell client, CleanupStage stage) throws IOException {
		switch(stage) {
			case Max:
			case SCP:
				if(remoteCertPath.isPresent()) {
					try {
						client.runCommand("rm", "-f", remoteCertPath.get());
					} catch(IOException e) {
						/* Nothing to do */
					}
				}

				try {
					client.runCommand("rm", "-f", remoteAgentPath);
				} catch(IOException e) {
					/* Nothing to do */
				}
			case TmpDir:
				try {
					client.runCommand("rmdir", nimrodHomeDir);
				} catch(IOException e) {
					/* Nothing to do */
				}
			case Client:
				client.close();
		}
	}

	@Override
	public final LaunchResult[] launchAgents(Request... requests) throws IOException {
		if(isClosed) {
			return ActuatorUtils.makeFailedLaunch(requests, new IllegalStateException("actuator closed"));
		}

		try(RemoteShell client = makeClient()) {
			return launchAgents(client, requests);
		}
	}

	protected abstract LaunchResult[] launchAgents(RemoteShell shell, Request[] requests) throws IOException;

	@Override
	public final Resource getResource() {
		return node;
	}

	@Override
	public final void forceTerminateAgent(UUID[] uuids) {
		if(isClosed) {
			return;
		}

		try(RemoteShell client = makeClient()) {
			forceTerminateAgent(client, uuids);
		} catch(IOException e) {
			LOGGER.error("Unable to force terminate agents", e);
		}
	}

	protected abstract void forceTerminateAgent(RemoteShell shell, UUID[] uuids);

	@Override
	public final boolean isClosed() {
		return isClosed;
	}

	@Override
	public void close() throws IOException {
		if(isClosed) {
			return;
		}

		try(RemoteShell client = makeClient()) {
			try {
				close(client);
			} catch(IOException e) {
				/* nop */
			}
			doCleanup(client, CleanupStage.Max);
		} finally {
			isClosed = true;
		}
	}

	protected void close(RemoteShell shell) throws IOException {

	}
}
