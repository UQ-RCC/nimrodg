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
package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType.ClusterConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.resource.act.POSIXActuator;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

public abstract class ClusterActuator<C extends ClusterConfig> extends POSIXActuator<C> {

	@SuppressWarnings("WeakerAccess")
	protected static class TempBatch {

		public final UUID batchUuid;
		public final String batchDir;
		public final String scriptPath;
		public final String script;
		public final String stdoutPath;
		public final String stderrPath;
		public final int from;
		public final int to;
		public final UUID[] uuids;
		public final String[] configPath;
		public final JsonObject[] config;

		TempBatch(UUID batchUuid, String batchDir, String scriptPath, String script, String stdoutPath, String stderrPath, int from, int to, UUID[] uuids, String[] configPath, JsonObject[] config) {
			this.batchUuid = batchUuid;
			this.batchDir = batchDir;
			this.scriptPath = scriptPath;
			this.script = script;
			this.stdoutPath = stdoutPath;
			this.stderrPath = stderrPath;
			this.from = from;
			this.to = to;
			this.uuids = uuids;
			this.configPath = configPath;
			this.config = config;
		}
	}

	protected static final class Batch {
		final String jobId;
		final UUID uuid;
		final String batchDir;
		final LaunchResult[] results;
		final UUID[] uuids;

		Batch(String jobId, UUID uuid, String batchDir, int size) {
			this.jobId = jobId;
			this.uuid = uuid;
			this.batchDir = batchDir;
			this.results = new LaunchResult[size];
			this.uuids = new UUID[size];
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(POSIXActuator.class);

	private final ConcurrentHashMap<UUID, Batch> jobNames;
	protected final JsonObject baseConfig;

	public ClusterActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, C cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		this.jobNames = new ConcurrentHashMap<>();

		baseConfig = ActuatorUtils.buildBaseAgentConfig(
				amqpUri,
				this.routingKey,
				this.remoteCertPath,
				false,
				true,
				false,
				ActuatorUtils.resolveEnvironment(cfg.forwardedEnvironment)
		).build();
	}

	protected abstract String submitBatch(RemoteShell shell, TempBatch batch) throws IOException;

	/**
	 * Given a list of agent UUIDs, fail any that would kick us above our limit and return a new array of agents that
	 * are allowed to be spawned.
	 *
	 * @param uuids   An array of agent UUIDs.
	 * @param results An array of launch results. Must be the same length as uuids.
	 * @return The new list of UUIDs.
	 */
	protected UUID[] applyFullCap(UUID[] uuids, LaunchResult[] results) {
		LaunchResult fullLaunch = new LaunchResult(null, new NimrodException.ResourceFull(node));

		int currentAgents = jobNames.size();

		/* If this will send us over our limit, cap it and mark the excess as failed. */
		if(currentAgents + uuids.length >= config.limit) {
			UUID[] _uuids = Arrays.copyOf(uuids, config.limit - currentAgents);
			for(int i = _uuids.length; i < uuids.length; ++i) {
				results[i] = fullLaunch;
			}
			uuids = _uuids;
		}

		return uuids;
	}

	private static String buildConfigPath(String batchDir, UUID uuid) {
		return ActuatorUtils.posixJoinPaths(batchDir, "config-" + uuid.toString() + ".json");
	}

	/**
	 * Given a list of agent UUIDs, generate a list of batches to submit.
	 *
	 * @param requests The list of agent requests.
	 * @param uuids The list of agent UUIDs, for convenience.
	 * @return A list of batches to submit.
	 */
	protected TempBatch[] calculateBatches(Request[] requests, UUID[] uuids) {
		assert requests.length == uuids.length;

		int nBatches = (uuids.length / config.maxBatchSize) + ((uuids.length % config.maxBatchSize) >= 1 ? 1 : 0);
		TempBatch[] batches = new TempBatch[nBatches];

		for(int i = 0, launchIndex = 0; i < nBatches && launchIndex < uuids.length; ++i) {
			int batchSize = Math.min(config.maxBatchSize, uuids.length - launchIndex);
			UUID[] agentUuids = Arrays.copyOfRange(uuids, launchIndex, launchIndex + batchSize);

			UUID batchUuid = UUID.randomUUID();
			String batchDir = ActuatorUtils.posixJoinPaths(this.nimrodHomeDir, "batch-" + batchUuid.toString());

			String[] configPath = Arrays.stream(agentUuids)
					.map(u -> buildConfigPath(batchDir, u))
					.toArray(String[]::new);

			JsonObject[] config = Arrays.stream(requests)
					.map(r -> Json.createObjectBuilder(baseConfig).add("uuid", r.uuid.toString()).build())
					.toArray(JsonObject[]::new);

			String stdout = ActuatorUtils.posixJoinPaths(batchDir, "stdout.txt");
			String stderr = ActuatorUtils.posixJoinPaths(batchDir, "stderr.txt");
			String pbsPath = ActuatorUtils.posixJoinPaths(batchDir, "job.sh");
			String pbsScript = buildSubmissionScript(batchUuid, agentUuids, configPath, stdout, stderr);

			batches[i] = new TempBatch(batchUuid, batchDir, pbsPath, pbsScript, stdout, stderr, launchIndex, launchIndex + batchSize, agentUuids, configPath, config);
			launchIndex = batches[i].to;
		}
		return batches;
	}

	protected abstract String buildSubmissionScript(UUID batchUuid, UUID[] agentUuids, String[] configPaths, String out, String err);

	@Override
	public LaunchResult[] launchAgents(RemoteShell shell, Request[] requests) throws IOException {
		LaunchResult[] lr = new LaunchResult[requests.length];
		UUID[] uuids = Arrays.stream(requests)
				.map(r -> r.uuid)
				.toArray(UUID[]::new);
		uuids = applyFullCap(uuids, lr);
		/* Calculate each batch. */
		TempBatch[] batches = calculateBatches(requests, uuids);

		/* Now do things that can actually fail. */
		Instant utcNow = Instant.now(Clock.systemUTC());

		for(TempBatch tb : batches) {
			RemoteShell.CommandResult cr;

			cr = shell.runCommand("mkdir", "-p", tb.batchDir);
			if(cr.status != 0) {
				throw new IOException("Unable to create batch directory");
			}

			shell.upload(tb.scriptPath, tb.script.getBytes(StandardCharsets.UTF_8), EnumSet.of(PosixFilePermission.OWNER_READ), utcNow);

			for(int i = 0; i < tb.uuids.length; ++i) {
				shell.upload(tb.configPath[i], tb.config[i].toString().getBytes(StandardCharsets.UTF_8), EnumSet.of(PosixFilePermission.OWNER_READ), utcNow);
			}

			String jobId;
			try {
				jobId = submitBatch(shell, tb);
			} catch(IOException e) {
				LaunchResult res = new LaunchResult(null, e);
				for(int i = tb.from; i < tb.to; ++i) {
					lr[i] = res;
				}

				return lr;
			}

			Batch b = new Batch(jobId, tb.batchUuid, tb.batchDir, tb.to - tb.from);
			for(int i = 0; i < b.results.length; ++i) {
				b.results[i] = new LaunchResult(node, null, null, Json.createObjectBuilder()
						.add("batch_id", b.jobId)
						.add("batch_uuid", b.uuid.toString())
						.add("batch_dir", b.batchDir)
						.add("batch_size", b.results.length)
						.add("batch_index", i)
						.build());

				lr[tb.from + i] = b.results[i];
			}
			Arrays.setAll(b.uuids, i -> tb.uuids[i]);
			Arrays.stream(tb.uuids).forEach(u -> jobNames.put(u, b));
		}

		return lr;
	}

	@Override
	public final void forceTerminateAgent(RemoteShell shell, UUID[] uuids) {
		/* Filter whole batches. */
		Map<Batch, List<UUID>> batches = NimrodUtils.mapToParent(Arrays.stream(uuids), jobNames::get).entrySet().stream()
				.filter(e -> Set.of(e.getKey().uuids).equals(new HashSet<>(e.getValue())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		String[] jobs = batches.keySet().stream().map(b -> b.jobId).distinct().toArray(String[]::new);
		if(jobs.length > 0 && !killJobs(shell, jobs)) {
			return;
		}

		for(UUID u : uuids) {
			jobNames.remove(u);
		}
	}

	/**
	 * @param shell  The remote shell to use.
	 * @param jobIds A list of batch system job identifiers to kill. This will never be empty.
	 * @return If all of the jobs were able to be killed, returns true. Otherwise, false.
	 */
	protected abstract boolean killJobs(RemoteShell shell, String[] jobIds);

	@Override
	protected final void close(RemoteShell shell) {
		/*
		 * Attempt to cleanup the config files as they may contain secrets.
		 * Leave the rest for easier debugging.
		 */
		String[] args = Stream.concat(Stream.of("rm", "-f"), this.jobNames.values().stream()
				.flatMap(b -> Arrays.stream(b.uuids)
						.filter(Objects::nonNull) /* NB: Will happen if only a partial batch has been adopted. */
						.map(u -> buildConfigPath(b.batchDir, u)))
		).toArray(String[]::new);

		try {
			shell.runCommand(args);
		} catch(IOException e) {
			LOGGER.warn("Unable to remove configuration files", e);
		}

		String[] jobs = jobNames.values().stream().map(b -> b.jobId).distinct().toArray(String[]::new);
		if(jobs.length > 0)
			killJobs(shell, jobs);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {

	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		if(isClosed) {
			return;
		}

		/* Agent gone, remove its name */
		jobNames.remove(uuid);
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

		JsonString jbatchid = data.getJsonString("batch_id");
		if(jbatchid == null) {
			return AdoptStatus.Rejected;
		}

		JsonString jbatchuuid = data.getJsonString("batch_uuid");
		if(jbatchuuid == null) {
			return AdoptStatus.Rejected;
		}

		UUID batchUuid;

		try {
			batchUuid = UUID.fromString(jbatchuuid.getString());
		} catch(IllegalArgumentException e) {
			return AdoptStatus.Rejected;
		}

		JsonString jbatchdir = data.getJsonString("batch_dir");
		if(jbatchdir == null) {
			return AdoptStatus.Rejected;
		}

		JsonNumber jbatchsize = data.getJsonNumber("batch_size");
		if(jbatchsize == null) {
			return AdoptStatus.Rejected;
		}

		JsonNumber jbatchindex = data.getJsonNumber("batch_index");
		if(jbatchindex == null) {
			return AdoptStatus.Rejected;
		}

		String batchId = jbatchid.getString();
		String batchDir = jbatchdir.getString();
		int batchSize = jbatchsize.intValue();
		int batchIndex = jbatchindex.intValue();

		Batch batch = jobNames.get(state.getUUID());
		if(batch == null) {
			batch = new Batch(batchId, batchUuid, batchDir, batchSize);
		}

		if(!batch.jobId.equals(batchId)) {
			return AdoptStatus.Rejected;
		}

		if(!batch.uuid.equals(batchUuid)) {
			return AdoptStatus.Rejected;
		}

		if(!batch.batchDir.equals(batchDir)) {
			return AdoptStatus.Rejected;
		}

		if(batch.results.length != batchSize || batchIndex >= batchSize) {
			return AdoptStatus.Rejected;
		}

		if(batch.uuids[batchIndex] == state.getUUID()) {
			return AdoptStatus.Rejected;
		}

		if(batch.uuids[batchIndex] != state.getUUID() && batch.uuids[batchIndex] != null) {
			return AdoptStatus.Rejected;
		}

		batch.uuids[batchIndex] = state.getUUID();
		batch.results[batchIndex] = new LaunchResult(node, null, state.getExpiryTime(), data);
		jobNames.putIfAbsent(state.getUUID(), batch);

		return AdoptStatus.Adopted;
	}

	@Override
	public final boolean canSpawnAgents(int num) throws IllegalArgumentException {
		if(isClosed) {
			return false;
		}

		return jobNames.size() + num <= config.limit;
	}
}
