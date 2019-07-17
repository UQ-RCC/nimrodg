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
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceFullException;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.cluster.ClusterResourceType.BatchedClusterConfig;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.act.POSIXActuator;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

public abstract class ClusterActuator<C extends BatchedClusterConfig> extends POSIXActuator<C> {

	protected static class TempBatch {

		public final UUID batchUuid;
		public final String scriptPath;
		public final String script;
		public final String stdoutPath;
		public final String stderrPath;
		public final int from;
		public final int to;
		public final UUID[] uuids;

		public TempBatch(UUID batchUuid, String scriptPath, String script, String stdoutPath, String stderrPath, int from, int to, UUID[] uuids) {
			this.batchUuid = batchUuid;
			this.scriptPath = scriptPath;
			this.script = script;
			this.stdoutPath = stdoutPath;
			this.stderrPath = stderrPath;
			this.from = from;
			this.to = to;
			this.uuids = uuids;
		}
	}

	protected static final class Batch {

		public final String jobId;
		public final LaunchResult[] results;
		public final UUID[] uuids;

		public Batch(String jobId, int size) {
			this.jobId = jobId;
			this.results = new LaunchResult[size];
			this.uuids = new UUID[size];
		}
	}

	protected final ConcurrentHashMap<UUID, Batch> jobNames;

	public ClusterActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, C cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		this.jobNames = new ConcurrentHashMap<>();
	}

	/**
	 * Apply the submission arguments to the job script.
	 *
	 * @param sb The submission script. This is just after the crunchbang.
	 * @param uuids The list of UUIDs being submitted.
	 */
	private void applySubmissionArguments(StringBuilder sb, UUID[] uuids) {
		String[] args = config.dialect.buildSubmissionArguments(uuids.length, config.batchConfig, config.submissionArgs);
		applyBatchedSubmissionArguments(sb, uuids, args);
	}

	/**
	 * Apply the processed submission arguments to the job script.
	 *
	 * @param sb The submission script. This is just after the crunchbang.
	 * @param uuids The list of UUIDs being submitted.
	 * @param processedArgs The submission arguments after being processed by the dialect.
	 */
	protected abstract void applyBatchedSubmissionArguments(StringBuilder sb, UUID[] uuids, String[] processedArgs);

	protected abstract String submitBatch(RemoteShell shell, TempBatch batch) throws IOException;

	/**
	 * Given a list of agent UUIDs, fail any that would kick us above our limit and return a new array of agents that
	 * are allowed to be spawned.
	 *
	 * @param uuids An array of agent UUIDs.
	 * @param results An array of launch results. Must be the same length as uuids.
	 * @return The new list of UUIDs.
	 */
	protected UUID[] applyFullCap(UUID[] uuids, LaunchResult[] results) {
		LaunchResult fullLaunch = new LaunchResult(null, new ResourceFullException(node));

		int currentAgents = nimrod.getResourceAgents(node).size();

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

	/**
	 * Given a list of agent UUIDs, generate a list of batches to submit.
	 *
	 * @param uuids An array of agent UUIDs.
	 * @return A list of batches to submit.
	 */
	protected TempBatch[] calculateBatches(UUID[] uuids) {
		int nBatches = (uuids.length / config.maxBatchSize) + ((uuids.length % config.maxBatchSize) >= 1 ? 1 : 0);
		TempBatch[] batches = new TempBatch[nBatches];

		for(int i = 0, launchIndex = 0; i < nBatches && launchIndex < uuids.length; ++i) {
			int batchSize = Math.min(config.maxBatchSize, uuids.length - launchIndex);
			UUID[] batchUuids = Arrays.copyOfRange(uuids, launchIndex, launchIndex + batchSize);

			UUID batchUuid = UUID.randomUUID();
			String pbsScript = ActuatorUtils.posixBuildSubmissionScriptMulti(
					batchUuids,
					String.format("$%s", config.tmpVar),
					uri,
					routingKey,
					this.remoteAgentPath,
					this.remoteCertPath,
					false,
					true,
					this::applySubmissionArguments
			);
			String pbsPath = ActuatorUtils.posixJoinPaths(this.nimrodHomeDir, batchUuid.toString());
			String stdout = ActuatorUtils.posixJoinPaths(this.nimrodHomeDir, String.format("batch-%s.stdout.txt", batchUuid));
			String stderr = ActuatorUtils.posixJoinPaths(this.nimrodHomeDir, String.format("batch-%s.stderr.txt", batchUuid));
			batches[i] = new TempBatch(batchUuid, pbsPath, pbsScript, stdout, stderr, launchIndex, launchIndex + batchSize, batchUuids);
			launchIndex = batches[i].to;
		}
		return batches;
	}

	@Override
	public LaunchResult[] launchAgents(RemoteShell shell, UUID[] uuids) throws IOException {
		LaunchResult[] lr = new LaunchResult[uuids.length];
		uuids = applyFullCap(uuids, lr);
		/* Calculate each batch. */
		TempBatch[] batches = calculateBatches(uuids);

		/* Now do things that can actually fail. */
		LaunchResult goodLaunch = new LaunchResult(node, null);
		Instant utcNow = Instant.now(Clock.systemUTC());
		for(TempBatch tb : batches) {
			shell.upload(tb.scriptPath, tb.script.getBytes(StandardCharsets.UTF_8), EnumSet.of(PosixFilePermission.OWNER_READ), utcNow);

			LaunchResult res;
			String jobId;
			try {
				jobId = submitBatch(shell, tb);
				Batch b = new Batch(jobId, tb.to - tb.from);
				res = goodLaunch;
				Arrays.setAll(b.results, i -> new LaunchResult(node, null, null, Json.createObjectBuilder()
						.add("batch_id", b.jobId)
						.add("batch_size", b.results.length)
						.add("batch_index", i)
						.build()));
				Arrays.setAll(b.uuids, i -> tb.uuids[i]);
				Arrays.stream(tb.uuids).forEach(u -> jobNames.put(u, b));
			} catch(IOException e) {
				res = new LaunchResult(null, e);
			}

			for(int i = tb.from; i < tb.to; ++i) {
				lr[i] = res;
			}
		}

		return lr;
	}

	@Override
	public boolean forceTerminateAgent(RemoteShell shell, UUID uuid) {
		Batch b = jobNames.getOrDefault(uuid, null);
		/* We can only kill batches of size 1. */
		if(b.results.length > 1) {
			return false;
		}

		if(killJob(shell, b.jobId)) {
			jobNames.remove(uuid);
			return true;
		}
		return false;
	}

	protected abstract boolean killJob(RemoteShell shell, String jobId);

	@Override
	public void close(RemoteShell shell) throws IOException {
		Set.copyOf(jobNames.values()).stream().forEach(batch -> killJob(shell, batch.jobId));
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		/* Set the walltime. */
		config.dialect.getWalltime(config.batchConfig)
				.ifPresent(l -> state.setExpiryTime(state.getConnectionTime().plusSeconds(l)));
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		/* Agent gone, remove its name */
		jobNames.remove(uuid);
	}

	@Override
	public boolean adopt(AgentState state) {
		JsonObject data = state.getActuatorData();
		if(data == null) {
			return false;
		}

		if(state.getState() == Agent.State.SHUTDOWN) {
			return false;
		}

		JsonString jbatchid = data.getJsonString("batch_id");
		if(jbatchid == null) {
			return false;
		}

		JsonNumber jbatchsize = data.getJsonNumber("batch_size");
		if(jbatchsize == null) {
			return false;
		}

		JsonNumber jbatchindex = data.getJsonNumber("batch_index");
		if(jbatchindex == null) {
			return false;
		}

		String batchId = jbatchid.getString();
		int batchSize = jbatchsize.intValue();
		int batchIndex = jbatchindex.intValue();

		Batch batch = jobNames.get(state.getUUID());
		if(batch == null) {
			batch = new Batch(batchId, jbatchsize.intValue());
		}

		if(!batch.jobId.equals(batchId)) {
			return false;
		}

		if(batch.results.length != batchSize || batchIndex >= batchSize) {
			return false;
		}

		if(batch.uuids[batchIndex] == state.getUUID()) {
			return true;
		}

		if(batch.uuids[batchIndex] != state.getUUID() && batch.uuids[batchIndex] != null) {
			return false;
		}

		batch.uuids[batchIndex] = state.getUUID();
		batch.results[batchIndex] = new LaunchResult(node, null, state.getExpiryTime(), data);
		jobNames.putIfAbsent(state.getUUID(), batch);

		return true;
	}

	@Override
	public final boolean canSpawnAgents(int num) throws IllegalArgumentException {
		return nimrod.getResourceAgents(node).stream().count() + num <= config.limit;
	}
}
