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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import au.edu.uq.rcc.nimrodg.api.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

public class LocalActuator implements Actuator {

	private static final Logger LOGGER = LoggerFactory.getLogger(LocalActuator.class);

	public enum CaptureMode {
		OFF,
		STREAM,
		COPY,
		INHERIT
	}

	public enum LocalState {
		NOT_CONNECTED,
		CONNECTED,
		DISCONNECTED
	}

	private static class LocalAgent {

		final UUID uuid;
		final Path workRoot;
		final Optional<Path> outputPath;
		final ProcessBuilder builder;
		Process process;
		ProcessHandle handle;
		CompletableFuture<Void> future;
		LocalState state;

		private LocalAgent(UUID uuid, Path workRoot, Optional<Path> outputPath, ProcessBuilder builder) {
			this.uuid = uuid;
			this.workRoot = workRoot;
			this.outputPath = outputPath;
			this.builder = builder;
			this.process = null;
			this.future = null;
			this.state = LocalState.NOT_CONNECTED;
		}

	}

	private final Operations ops;
	private final Path tmpRoot;
	private final Resource node;
	private final NimrodURI uri;
	private final String routingKey;
	private final Certificate[] certs;
	private final int parallelism;
	private final AgentInfo agentInfo;
	private final CaptureMode captureMode;
	private final Map<UUID, LocalAgent> agents;

	private boolean isClosed;

	public LocalActuator(Operations ops, Resource node, NimrodURI uri, Certificate[] certs, int parallelism, String platString, CaptureMode captureMode) throws IOException {
		this.ops = ops;
		NimrodConfig ncfg = ops.getConfig();
		this.tmpRoot = Paths.get(ncfg.getWorkDir()).resolve("localact-tmp");
		try {
			Files.createDirectories(tmpRoot);
		} catch(FileAlreadyExistsException e) {
			/* nop */
		}
		this.node = node;
		this.uri = uri;
		this.routingKey = ncfg.getAmqpRoutingKey();
		this.certs = Arrays.copyOf(certs, certs.length);
		this.parallelism = parallelism;
		if((this.agentInfo = ops.lookupAgentByPlatform(platString)) == null) {
			throw new IOException(String.format("No agent for platform string '%s'", platString));
		}
		this.captureMode = captureMode;

		this.agents = new HashMap<>();
		this.isClosed = false;
	}

	@Override
	public Resource getResource() throws NimrodException {
		return node;
	}

	private LocalAgent[] buildAgentInfo(UUID[] uuids) throws IOException {
		List<String> commonArgs = new ArrayList<>();
		String scheme = uri.uri.getScheme().toLowerCase(Locale.ENGLISH);
		commonArgs.add("--amqp-uri");
		commonArgs.add(uri.uri.toASCIIString());

		commonArgs.add("--amqp-routing-key");
		commonArgs.add(routingKey);

		Path tmpDir = Files.createTempDirectory("nimrodg-localact-");

		if(scheme.equals("amqps")) {
			if(uri.noVerifyPeer) {
				commonArgs.add("--no-verify-peer");
			}

			if(uri.noVerifyHost) {
				commonArgs.add("--no-verify-host");
			}

			Path certPath = tmpDir.resolve("certs.pem");
			ActuatorUtils.writeCertificatesToPEM(certPath, certs);
			commonArgs.add("--cacert");
			commonArgs.add(certPath.toString());

			commonArgs.add("--caenc");
			commonArgs.add("plain");

			commonArgs.add("--no-ca-delete");

		} else if(!scheme.equals("amqp")) {
			throw new IllegalArgumentException("Invalid URI scheme");
		}

		LocalAgent[] agents = new LocalAgent[uuids.length];
		for(int i = 0; i < uuids.length; ++i) {
			List<String> agentArgs = new ArrayList<>();
			agentArgs.add(agentInfo.getPath());

			agentArgs.add("--uuid");
			agentArgs.add(uuids[i].toString());

			agentArgs.addAll(commonArgs);

			Path workRoot = tmpDir.resolve(uuids[i].toString());
			agentArgs.add("--work-root");
			agentArgs.add(workRoot.toString());

			Optional<Path> outputPath = Optional.empty();

			ProcessBuilder pb = new ProcessBuilder(agentArgs);
			if(captureMode == CaptureMode.OFF) {
				pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			} else if(captureMode == CaptureMode.INHERIT) {
				pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
			} else {
				String fname = String.format("agent-%s.txt", uuids[i]);
				if(captureMode == CaptureMode.COPY) {
					outputPath = Optional.of(tmpDir.resolve(fname));
				} else {
					outputPath = Optional.of(tmpRoot.resolve(fname));
				}
				/* NB: Comment these for testing the master's robustness. Stdout will block. */
				pb.redirectOutput(ProcessBuilder.Redirect.to(outputPath.map(Path::toFile).get()));
			}

			pb.redirectErrorStream(true);

			agents[i] = new LocalAgent(uuids[i], workRoot, outputPath, pb);
		}

		return agents;
	}

	private Void agentShutdownHandler(LocalAgent la, Throwable t) {
		if(t != null) {
			if(LOGGER.isWarnEnabled()) {
				LOGGER.warn(String.format("Agent %s future failed exceptionally.", la.uuid), t);
			}
			ops.reportAgentFailure(this, la.uuid, AgentShutdown.Reason.HostSignal, 9);
		} else {
			int ret;
			if(la.process == null) {
				/* No way to get the return code of an orphaned agent, just use 0. */
				ret = 0;
			} else {
				ret = la.process.exitValue();
			}

			LOGGER.info("Agent {} process {} exited with return code {}.", la.uuid, la.handle.pid(), ret);

			/*
			 * Alright, we need to be a bit delicate here.
			 *
			 * The agent has three known return values:
			 * 0 - All good
			 * 1 - Something bad happened
			 * 2 - Invalid arguments
			 *
			 * If it's > 128, it was killed by a signal, which is (val - 128).
			 *
			 * The only time we can guarantee the final "agent.shutdown" message has been
			 * sent is when the return value is 0.
			 *
			 * There's a chance that the agent may have sent it's dying request, but report the failure anyway.
			 * The master is able to handle this.
			 */
			if(ret != 0) {
				if(ret > 128) {
					ops.reportAgentFailure(this, la.uuid, AgentShutdown.Reason.HostSignal, ret - 128);
				} else {
					ops.reportAgentFailure(this, la.uuid, AgentShutdown.Reason.HostSignal, -1);
				}
			}
		}

		if(captureMode == CaptureMode.COPY) {
			Path logPath = tmpRoot.resolve(String.format("agent-%s.txt", la.uuid));
			assert la.outputPath.isPresent();
			try {
				Files.move(la.outputPath.get(), logPath);
			} catch(IOException e) {
				LOGGER.error("Error copying agent output back.", e);
			}
		}

		try {
			NimrodUtils.deltree(la.workRoot);
		} catch(IOException e) {
			if(LOGGER.isErrorEnabled()) {
				LOGGER.error(String.format("Error cleaning up after agent %s.", la.uuid), e);
			}

		}

		synchronized(agents) {
			agents.remove(la.uuid);
		}

		return null;
	}

	@Override
	public LaunchResult[] launchAgents(UUID[] uuids) throws IOException {
		if(isClosed) {
			return ActuatorUtils.makeFailedLaunch(uuids, new IllegalStateException("actuator closed"));
		}

		LaunchResult[] results = new LaunchResult[uuids.length];
		LaunchResult failedResult = new LaunchResult(null, new NimrodException.ResourceFull(node));

		/* If we're chockers, just die */
		synchronized(agents) {
			if(agents.size() >= parallelism) {
				Arrays.fill(results, failedResult);
				return results;
			}
		}

		LocalAgent[] _agents = buildAgentInfo(uuids);

		for(int i = 0; i < _agents.length; ++i) {
			/* If we're full, fail the launch. */
			synchronized(agents) {
				if(agents.size() >= parallelism) {
					results[i] = failedResult;
					continue;
				}
			}

			LocalAgent la = _agents[i];
			try {
				la.process = la.builder.start();
			} catch(IOException | SecurityException e) {
				results[i] = new LaunchResult(null, e);
				continue;
			}

			la.handle = la.process.toHandle();

			synchronized(agents) {
				agents.put(la.uuid, la);
			}

			results[i] = new LaunchResult(node, null, null, Json.createObjectBuilder()
					.add("pid", la.handle.pid())
					.add("work_root", la.workRoot.toString())
					.add("output_path", la.outputPath.map(Path::toString).orElse(""))
					.build()
			);
			LOGGER.info("Launched agent {} with PID {}", la.uuid, la.handle.pid());
			la.future = la.process.onExit().handle((p, t) -> agentShutdownHandler(la, t));
		}

		return results;
	}

	@Override
	public void forceTerminateAgent(UUID[] uuid) {
		if(isClosed) {
			return;
		}

		LocalAgent[] las;
		synchronized(agents) {
			las = Arrays.stream(uuid)
					.map(agents::remove)
					.filter(Objects::nonNull)
					.toArray(LocalAgent[]::new);
		}

		for(LocalAgent la : las) {
			/*
			 * Call destroyForcibly() on it to give it a nudge.
			 * Regardless of what it does, obtrude the value just so the
			 * future's completed. Best case scenario, it dies and the cleanup runs
			 * silently.
			 */
			la.handle.destroyForcibly();
			la.future.obtrudeValue(null);
		}
	}

	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	@Override
	public void close() {
		if(isClosed) {
			return;
		}

		isClosed = true;

		CompletableFuture af;
		synchronized(agents) {
			af = CompletableFuture.allOf(agents.values().stream().map(a -> a.future).toArray(CompletableFuture[]::new));
			agents.values().stream().map(a -> a.handle).forEach(ProcessHandle::destroy);
		}

		long msWait = 5000;
		long timeWaited = 0;
		while(timeWaited < msWait) {
			long startTime = System.currentTimeMillis();
			try {
				af.get(msWait - timeWaited, TimeUnit.MILLISECONDS);
				break;
			} catch(InterruptedException | TimeoutException e) {
				/* nop */
			} catch(ExecutionException e) {
				LOGGER.error("Error waiting for local agents to die.", e);
				break;
			}

			timeWaited += System.currentTimeMillis() - startTime;
		}

		/* See if we're done or timed out. */
		if(!af.isDone()) {
			LOGGER.warn("Timed out waiting for {} agents to die.", agents.size());
		}
//		while(true) {
//			try {
//				af.get();
//				break;
//			} catch(InterruptedException e) {
//				/* nop */
//			} catch(ExecutionException e) {
//				LOGGER.error("Error waiting for local agents to die.");
//				LOGGER.catching(e);
//				break;
//			}
//		}
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		if(isClosed) {
			return;
		}

		LocalAgent la;
		synchronized(agents) {
			if((la = agents.get(state.getUUID())) == null) {
				return;
			}
		}

		// Good for testing
		//state.setExpiryTime(Instant.now(Clock.systemUTC()).plusSeconds(10));
		la.state = LocalState.CONNECTED;
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		if(isClosed) {
			return;
		}

		LocalAgent la;
		synchronized(agents) {
			if((la = agents.get(uuid)) == null) {
				return;
			}
		}

		la.state = LocalState.DISCONNECTED;
		/* Just in case */
		la.handle.destroy();
	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		if(isClosed) {
			return false;
		}

		return agents.size() + num <= parallelism;
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

		if(state.getState() == Agent.State.SHUTDOWN) {
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

		Optional<Path> outputPath = Optional.ofNullable(data.getJsonString("output_path"))
				.map(JsonString::getString)
				.filter(s -> !s.isEmpty())
				.map(s -> Paths.get(s));

		/* See if the process is alive. */
		Optional<ProcessHandle> oph = ProcessHandle.of(jpid.longValue());
		if(!oph.isPresent()) {
			return AdoptStatus.Stale;
		}

		LocalAgent la = new LocalAgent(
				state.getUUID(),
				Paths.get(jworkroot.getString()),
				outputPath,
				null
		);

		la.handle = oph.get();
		la.state = LocalState.CONNECTED;

		/* Only "adopt" us if we're not a dupe. */
		boolean adopted;
		synchronized(agents) {
			adopted = agents.putIfAbsent(state.getUUID(), la) == null;
		}

		/* Only add the shutdown handler if we were adopted. */
		if(adopted) {
			la.future = la.handle.onExit().handle((p, t) -> agentShutdownHandler(la, t));
		}

		return AdoptStatus.Adopted;
	}
}
