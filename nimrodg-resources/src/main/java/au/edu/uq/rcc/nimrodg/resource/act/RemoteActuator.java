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
package au.edu.uq.rcc.nimrodg.resource.act;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteActuator extends POSIXActuator<SSHResourceType.SSHConfig> {

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteActuator.class);

	public enum RemoteState {
		NOT_CONNECTED,
		CONNECTED,
		DISCONNECTED
	}

	private static class RemoteAgent {

		final UUID uuid;
		final int pid;
		final String workRoot;
		public RemoteState state;

		RemoteAgent(UUID uuid, int pid, String workRoot) {
			this.uuid = uuid;
			this.pid = pid;
			this.workRoot = workRoot;
			this.state = RemoteState.NOT_CONNECTED;
		}

	}

	private final int limit;
	private final String tmpDir;
	private final Map<UUID, RemoteAgent> agents;
	private final String[] agentCommand;

	public RemoteActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, int limit, String tmpDir, SSHResourceType.SSHConfig config) throws IOException {
		super(ops, node, amqpUri, certs, config);
		this.limit = limit;
		this.tmpDir = tmpDir;
		this.agents = new HashMap<>();
		this.agentCommand = new String[]{this.remoteAgentPath, "-c", "-"};
	}

	@Override
	protected LaunchResult[] launchAgents(RemoteShell shell, Request[] requests) throws IOException {
		LaunchResult[] results = new LaunchResult[requests.length];
		LaunchResult failedResult = new LaunchResult(null, new NimrodException.ResourceFull(node));

		if(agents.size() >= limit) {
			Arrays.fill(results, failedResult);
			return results;
		}

		//RemoteAgent[] _agents = buildAgentInfo(uuids);
		for(int i = 0; i < requests.length; ++i) {
			/* If we're full, fail the launch. */
			if(agents.size() >= limit) {
				results[i] = failedResult;
				continue;
			}

			String workRoot = ActuatorUtils.posixJoinPaths(tmpDir, String.format("agent-%s", requests[i].uuid));

			shell.runCommand("mkdir", "-p", workRoot);

			Optional<String> certPath = Optional.empty();
			if(this.certs.length > 0) {
				byte[] bcert = ActuatorUtils.writeCertificatesToPEM(this.certs);
				certPath = Optional.of(ActuatorUtils.posixJoinPaths(workRoot, "cert.pem"));
				shell.upload(certPath.get(), bcert, O600, Instant.now());
			}

			/* Generate the agent configuration. It's dumped straight to stdin and doesn't touch the disk. */
			byte[] input = ActuatorUtils.buildBaseAgentConfig(
					uri,
					routingKey,
					certPath,
					false,
					false,
					true,
					ops.getSigningAlgorithm(),
					ActuatorUtils.resolveEnvironment(this.config.forwardedEnvironment)
			).add("uuid", requests[i].uuid.toString())
					.add("secret_key", requests[i].secretKey)
					.add("work_root", workRoot)
					.build().toString().getBytes(StandardCharsets.UTF_8);

			RemoteShell.CommandResult cr = shell.runCommand(agentCommand, input);
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

			agents.put(requests[i].uuid, new RemoteAgent(requests[i].uuid, pid, workRoot));
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
				.map(agents::remove)
				.filter(Objects::nonNull)
				.mapToInt(ra -> ra.pid)
				.toArray()
		);
	}

	@Override
	protected void close(RemoteShell shell) {
		kill(shell, agents.values().stream().mapToInt(ra -> ra.pid).toArray());
	}

	public static void kill(RemoteShell shell, int[] pids) {
		if(pids.length == 0) {
			return;
		}

		String[] args = Stream.concat(Stream.of("kill", "-9"), IntStream.of(pids).mapToObj(String::valueOf)).toArray(String[]::new);

		try {
			shell.runCommand(args);
		} catch(IOException e) {
			if(LOGGER.isErrorEnabled()) {
				LOGGER.error(String.format("Unable to execute kill for jobs '%s'", String.join(",", args)), e);
			}
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
	public AdoptStatus adopt(AgentState state) {
		if(isClosed) {
			return AdoptStatus.Rejected;
		}

		JsonObject data = state.getActuatorData();
		if(data == null) {
			return AdoptStatus.Rejected;
		}

		if(state.getState() == AgentInfo.State.SHUTDOWN) {
			return AdoptStatus.Rejected;
		}

		JsonNumber jpid = data.getJsonNumber("pid");
		if(jpid == null) {
			return AdoptStatus.Rejected;
		}

		JsonString jworkroot = data.getJsonString("work_root");
		if(jworkroot == null) {
			return AdoptStatus.Rejected;
		}

		RemoteAgent ra = new RemoteAgent(state.getUUID(), jpid.intValue(), jworkroot.getString());
		ra.state = state.getState() == AgentInfo.State.WAITING_FOR_HELLO ? RemoteState.NOT_CONNECTED : RemoteState.CONNECTED;
		agents.putIfAbsent(ra.uuid, ra);

		return AdoptStatus.Adopted;
	}

	@Override
	protected AgentStatus queryStatus(RemoteShell shell, UUID uuid) {
		RemoteAgent ra = agents.get(uuid);
		if(ra == null) {
			return AgentStatus.Unknown;
		}

		switch(ra.state) {
			case CONNECTED:
				return AgentStatus.Connected;
			case DISCONNECTED:
				return AgentStatus.Disconnected;
			case NOT_CONNECTED:
				return AgentStatus.Launched;
		}
		return AgentStatus.Unknown;
	}
}
