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

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceFullException;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell.CommandResult;
import java.io.IOException;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RemoteActuator extends POSIXActuator<SSHResourceType.SSHConfig> {

	private static final Logger LOGGER = LogManager.getLogger(RemoteActuator.class);

	public enum RemoteState {
		NOT_CONNECTED,
		CONNECTED,
		DISCONNECTED
	}

	private class RemoteAgent {

		public final UUID uuid;
		public final int pid;
		public RemoteState state;

		public RemoteAgent(UUID uuid, int pid) {
			this.uuid = uuid;
			this.pid = pid;
			this.state = RemoteState.NOT_CONNECTED;
		}

	}

	private final int limit;
	private final String tmpDir;
	private final Map<UUID, RemoteAgent> agents;

	public RemoteActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, int limit, String tmpDir, SSHResourceType.SSHConfig config) throws IOException {
		super(ops, node, amqpUri, certs, config);
		this.limit = limit;
		this.tmpDir = tmpDir;
		this.agents = new HashMap<>();
	}

	@Override
	protected LaunchResult[] launchAgents(RemoteShell shell, UUID[] uuids) throws IOException {
		LaunchResult[] results = new LaunchResult[uuids.length];
		LaunchResult failedResult = new LaunchResult(null, new ResourceFullException(node));

		if(agents.size() >= limit) {
			Arrays.fill(results, failedResult);
			return results;
		}

		//RemoteAgent[] _agents = buildAgentInfo(uuids);
		for(int i = 0; i < uuids.length; ++i) {
			/* If we're full, fail the launch. */
			if(agents.size() >= limit) {
				results[i] = failedResult;
				continue;
			}

			String workDir = ActuatorUtils.posixJoinPaths(tmpDir, String.format("agent-%s", uuids[i]));

			shell.runCommand("mkdir", "-p", workDir);

			Optional<String> certPath = Optional.empty();
			if(this.certs.length > 0) {
				byte[] bcert = ActuatorUtils.writeCertificatesToPEM(this.certs);
				certPath = Optional.of(ActuatorUtils.posixJoinPaths(workDir, "cert.pem"));
				shell.upload(certPath.get(), bcert, O600, Instant.now());
			}

			/* Build the agent launch command line */
			ArrayList<String> args = ActuatorUtils.posixBuildLaunchCommand(
					this.remoteAgentPath,
					uuids[i],
					workDir,
					uri,
					routingKey,
					certPath,
					false,
					false,
					true
			);

			String[] _args = args.stream().toArray(String[]::new);
			CommandResult cr = shell.runCommand(_args);
			if(cr.status != 0) {
				results[i] = new LaunchResult(null, new IOException(String.format("Remote command execution failed: %s", cr.stderr.trim())));
				continue;
			}

			if(!cr.stderr.isEmpty()) {
				LOGGER.warn("Remote stderr not empty: {}", cr.stderr);
			}

			int pid;
			try {
				pid = Integer.parseUnsignedInt(cr.stdout.trim(), 10);
			} catch(NumberFormatException e) {
				results[i] = new LaunchResult(null, e);
				continue;
			}

			agents.put(uuids[i], new RemoteAgent(uuids[i], pid));
			results[i] = new LaunchResult(node, null);
		}

		return results;
	}

	@Override
	protected boolean forceTerminateAgent(RemoteShell shell, UUID uuid) {
		RemoteAgent ra = agents.remove(uuid);
		if(ra == null) {
			return false;
		}

		return kill(shell, ra.pid);
	}

	private boolean kill(RemoteShell shell, int pid) {
		try {
			shell.runCommand("kill", "-9", Integer.toString(pid));
			return true;
		} catch(IOException e) {
			LOGGER.catching(e);
			return false;
		}
	}

	@Override
	protected void close(RemoteShell shell) throws IOException {
		agents.values().stream().forEach(ra -> kill(shell, ra.pid));
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		RemoteAgent ra = agents.get(state.getUUID());
		if(ra == null) {
			return;
		}

		ra.state = RemoteState.CONNECTED;
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		RemoteAgent ra = agents.remove(uuid);
		if(ra == null) {
			return;
		}

		ra.state = RemoteState.DISCONNECTED;
	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		return agents.size() + num <= limit;
	}

}
