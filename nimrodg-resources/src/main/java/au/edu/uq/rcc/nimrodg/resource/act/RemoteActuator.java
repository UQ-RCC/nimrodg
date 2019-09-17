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

import au.edu.uq.rcc.nimrodg.agent.Agent;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
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
		public final String workRoot;
		public RemoteState state;

		public RemoteAgent(UUID uuid, int pid, String workRoot) {
			this.uuid = uuid;
			this.pid = pid;
			this.workRoot = workRoot;
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

			String workRoot = ActuatorUtils.posixJoinPaths(tmpDir, String.format("agent-%s", uuids[i]));

			shell.runCommand("mkdir", "-p", workRoot);

			Optional<String> certPath = Optional.empty();
			if(this.certs.length > 0) {
				byte[] bcert = ActuatorUtils.writeCertificatesToPEM(this.certs);
				certPath = Optional.of(ActuatorUtils.posixJoinPaths(workRoot, "cert.pem"));
				shell.upload(certPath.get(), bcert, O600, Instant.now());
			}

			/* Build the agent launch command line */
			ArrayList<String> args = ActuatorUtils.posixBuildLaunchCommand(
					this.remoteAgentPath,
					uuids[i],
					workRoot,
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

			agents.put(uuids[i], new RemoteAgent(uuids[i], pid, workRoot));
			results[i] = new LaunchResult(node, null, null, Json.createObjectBuilder()
					.add("pid", pid)
					.add("work_root", workRoot)
					.build());
		}

		return results;
	}

	@Override
	protected void forceTerminateAgent(RemoteShell shell, UUID[] uuids) {
		kill(shell, Arrays.stream(uuids)
				.map(u -> agents.remove(u))
				.filter(ra -> ra != null)
				.mapToInt(ra -> ra.pid)
				.toArray()
		);
	}

	@Override
	protected void close(RemoteShell shell) throws IOException {
		kill(shell, agents.values().stream().mapToInt(ra -> ra.pid).toArray());
	}

	private boolean kill(RemoteShell shell, int[] pids) {
		if(pids.length == 0) {
			return true;
		}

		String[] args = Stream.concat(Stream.of("kill", "-9"), IntStream.of(pids).mapToObj(i -> String.valueOf(i)))
				.toArray(String[]::new);

		try {
			shell.runCommand(args);
			return true;
		} catch(IOException e) {
			LOGGER.catching(e);
			return false;
		}
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		if(isClosed) {
			return;
		}

		RemoteAgent ra = agents.get(state.getUUID());
		if(ra == null) {
			return;
		}

		ra.state = RemoteState.CONNECTED;
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		if(isClosed) {
			return;
		}

		RemoteAgent ra = agents.remove(uuid);
		if(ra == null) {
			return;
		}

		ra.state = RemoteState.DISCONNECTED;
	}

	@Override
	public boolean canSpawnAgents(int num) {
		if(isClosed) {
			return false;
		}

		return agents.size() + num <= limit;
	}

	@Override
	public boolean adopt(AgentState state) {
		if(isClosed) {
			return false;
		}

		JsonObject data = state.getActuatorData();
		if(data == null) {
			return false;
		}

		if(state.getState() == Agent.State.SHUTDOWN) {
			return false;
		}

		JsonNumber jpid = data.getJsonNumber("pid");
		if(jpid == null) {
			return false;
		}

		JsonString jworkroot = data.getJsonString("work_root");
		if(jworkroot == null) {
			return false;
		}

		RemoteAgent ra = new RemoteAgent(state.getUUID(), jpid.intValue(), jworkroot.getString());
		ra.state = state.getState() == Agent.State.WAITING_FOR_HELLO ? RemoteState.NOT_CONNECTED : RemoteState.CONNECTED;
		agents.putIfAbsent(ra.uuid, ra);

		return true;
	}
}
