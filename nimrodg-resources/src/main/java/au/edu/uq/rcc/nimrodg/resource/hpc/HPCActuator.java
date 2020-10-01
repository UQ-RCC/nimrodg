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
package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.resource.act.POSIXActuator;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

public final class HPCActuator extends POSIXActuator<HPCConfig> {

	private static final Jinjava TEMPLATE_ENGINE = new Jinjava() {{
		this.getGlobalContext().registerFilter(new Filter() {
			@Override
			public Object filter(Object o, JinjavaInterpreter ji, String... strings) {
				if(o == null) {
					return null;
				}
				return ShellUtils.quoteArgument(o.toString());
			}

			@Override
			public String getName() {
				return "quote";
			}
		});
	}};

	public static final Map<String, Object> TEMPLATE_SAMPLE_VARS = new HashMap<>(){{
		UUID[] uuids = {
				UUID.fromString("a0ffd1ac-db63-4c6e-b661-f9c9349afccd"),
				UUID.fromString("09814659-2674-4180-aa60-d7a2ebcc26fa"),
				UUID.fromString("3aa4bd16-d425-4171-ae6d-bc5294289d82"),
				UUID.fromString("63203256-fa7a-4ff0-b336-e0b3fea4808c"),
				UUID.fromString("dfe82a4b-e295-4128-9180-afd83eb1c756"),
				UUID.fromString("98028d6e-881b-4ae0-aff7-199a8dd26fea"),
				UUID.fromString("94e98f08-0a44-4693-8202-447377e58f65"),
				UUID.fromString("8752a071-4bac-4508-9881-49b70e2fa6ae"),
				UUID.fromString("c67de3a0-ec19-4780-b533-828999482e3a"),
				UUID.fromString("73f5c6e7-afca-466d-aba6-b85aec2c93da")
		};
		put("batch_uuid", "57ace1d4-0f8d-4439-9181-0fe91d6d73d4");
		put("batch_size", uuids.length);
		put("batch_walltime", 86400);
		put("output_path", "/remote/path/to/stdout.txt");
		put("error_path", "/remote/path/to/stderr.txt");
		put("job_queue", "workq");
		put("job_server", "tinmgr2.ib0");
		put("job_account", "account");
		put("job_ncpus", 12);
		put("job_mem", 4294967296L);
		put("agent_binary", "/remote/path/to/agent/binary");
		put("agent_uuids", uuids);
		put("agent_args", Map.of(
				"amqp_uri", "amqps://user:pass@host:port/vhost",
				"amqp_routing_key", "routingkey",
				"amqp_no_verify_peer", true,
				"amqp_no_verify_host", false,
				"cacert", "/path/to/cert.pem",
				"caenc", "b64",
				"no_ca_delete", true
		));
		put("config_path", "/path/to/remote/config.json");
	}};

	public static String renderTemplate(String template, Map<String, ?> vars) {
		return TEMPLATE_ENGINE.render(template, vars);
	}

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

	private static final int CSTATE_NONE = 0;
	private static final int CSTATE_CONNECTED = 1;
	private static final int CSTATE_DISCONNECTED = 2;

	protected static final class Batch {
		final String jobId;
		final UUID uuid;
		final String batchDir;
		final LaunchResult[] results;
		final UUID[] uuids;
		final AtomicInteger[] connectState;
		final AtomicReferenceArray<AgentStatus> statuses;
		final String script;
		final String scriptPath;
		final String[] configPath;
		final Map<UUID, Integer> indexMap;

		Batch(String jobId, UUID uuid, String batchDir, int size, String script, String scriptPath) {
			this.jobId = jobId;
			this.uuid = uuid;
			this.batchDir = batchDir;
			this.results = new LaunchResult[size];
			this.uuids = new UUID[size];
			this.connectState = new AtomicInteger[size];
			Arrays.fill(connectState, new AtomicInteger(CSTATE_NONE));
			this.statuses = new AtomicReferenceArray<>(size);
			this.script = script;
			this.scriptPath = scriptPath;
			this.configPath = new String[size];
			this.indexMap = new HashMap<>(size);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(POSIXActuator.class);

	private final ConcurrentHashMap<UUID, Batch> jobNames;

	protected final JsonObject baseConfig;
	private final ScheduledExecutorService /* suddenly */ ses;

	/** Set of files to be deleted upon {@link HPCActuator#close()}. */
	private final HashSet<String> remoteFiles;

	public HPCActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, HPCConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		this.jobNames = new ConcurrentHashMap<>();

		baseConfig = ActuatorUtils.buildBaseAgentConfig(
				amqpUri,
				this.routingKey,
				this.remoteCertPath,
				false,
				true,
				false,
				ops.getSigningAlgorithm(),
				ActuatorUtils.resolveEnvironment(cfg.forwardedEnvironment)
		).build();

		this.remoteFiles = new HashSet<>();

		this.ses = Executors.newSingleThreadScheduledExecutor();
		this.ses.scheduleAtFixedRate(() -> {
			try {
				this.pollThread();
			} catch(RuntimeException e) {
				LOGGER.error("Unable to query job status.", e);
			}
		}, 0, cfg.queryInterval, TimeUnit.SECONDS);
	}

	private String submitBatch(RemoteShell shell, TempBatch batch) throws IOException {
		return config.hpc.submit.execute(shell, batch.scriptPath).jobId;
	}

	/**
	 * Given a list of agent launch requests, fail any that would kick us above our limit and return a new
	 * array of requests that are allowed to be spawned.
	 *
	 * @param requests An array of agent launch requests.
	 * @param results 	An array of launch results. Must be the same length as requests.
	 * @return The new list of UUIDs.
	 */
	protected List<Request> applyFullCap(List<Request> requests, LaunchResult[] results) {
		LaunchResult fullLaunch = new LaunchResult(null, new NimrodException.ResourceFull(node));

		int currentAgents = jobNames.size();

		/* If this will send us over our limit, cap it and mark the excess as failed. */
		if(currentAgents + requests.size() >= config.limit) {
			List<Request> _requests = requests.subList(0, config.limit - currentAgents);
			for(int i = _requests.size(); i < requests.size(); ++i) {
				results[i] = fullLaunch;
			}
			requests = _requests;
		}

		return requests;
	}

	private TempBatch buildBatch(UUID batchUuid, List<Request> requests, int startIndex) {
		UUID[] uuids = requests.stream().map(r -> r.uuid).toArray(UUID[]::new);

		String batchDir = ActuatorUtils.posixJoinPaths(nimrodHomeDir, "batch-" + batchUuid.toString());

		String[] configPath = requests.stream()
				.map(r -> ActuatorUtils.posixJoinPaths(batchDir, "config-" + r.uuid.toString() + ".json"))
				.toArray(String[]::new);

		JsonObject[] config = requests.stream()
				.map(r -> Json.createObjectBuilder(baseConfig)
						.add("uuid", r.uuid.toString())
						.add("secret_key", r.secretKey)
						.build())
				.toArray(JsonObject[]::new);

		String stdout = ActuatorUtils.posixJoinPaths(batchDir, "stdout.txt");
		String stderr = ActuatorUtils.posixJoinPaths(batchDir, "stderr.txt");
		String pbsPath = ActuatorUtils.posixJoinPaths(batchDir, "job.sh");
		String pbsScript = buildSubmissionScript(batchUuid, uuids, configPath, stdout, stderr);

		return new TempBatch(batchUuid, batchDir, pbsPath, pbsScript, stdout, stderr,
				startIndex, startIndex + requests.size(), uuids, configPath, config);
	}

	/**
	 * Generate a list of batches to submit.
	 *
	 * @param requests The list of agent launch requests.
	 * @return A list of batches to submit.
	 */
	protected TempBatch[] calculateBatches(List<Request> requests) {
		int nBatches = (requests.size() / config.maxBatchSize) + ((requests.size() % config.maxBatchSize) >= 1 ? 1 : 0);
		TempBatch[] batches = new TempBatch[nBatches];

		for(int i = 0, launchIndex = 0; i < nBatches && launchIndex < requests.size(); ++i) {
			int batchSize = Math.min(config.maxBatchSize, requests.size() - launchIndex);
			batches[i] = buildBatch(UUID.randomUUID(), requests.subList(launchIndex, launchIndex + batchSize), launchIndex);
			launchIndex += batchSize;
		}
		return batches;
	}

	private String buildSubmissionScript(UUID batchUuid, UUID[] agentUuids, String[] configPath, String out, String err) {
		Map<String, Object> agentVars = new HashMap<>();
		agentVars.put("amqp_uri", uri.uri);
		agentVars.put("amqp_routing_key", routingKey);
		agentVars.put("amqp_no_verify_peer", uri.noVerifyPeer);
		agentVars.put("amqp_no_verify_host", uri.noVerifyHost);
		this.remoteCertPath.ifPresent(p -> {
			agentVars.put("cacert", p);
			agentVars.put("caenc", "plain");
			agentVars.put("no_ca_delete", true);
		});
		agentVars.put("output", "workroot");
		agentVars.put("batch", false);

		Map<String, Object> vars = new HashMap<>();
		vars.put("batch_uuid", batchUuid);
		vars.put("batch_size", agentUuids.length);
		vars.put("batch_walltime", config.walltime);
		vars.put("output_path", out);
		vars.put("error_path", err);
		config.account.ifPresent(acc -> vars.put("job_account", acc));
		config.queue.ifPresent(q -> vars.put("job_queue", q));
		config.server.ifPresent(s -> vars.put("job_server", s));
		vars.put("job_ncpus", config.ncpus);
		vars.put("job_mem", config.mem);
		vars.put("agent_binary", this.remoteAgentPath);
		vars.put("agent_uuids", agentUuids);
		vars.put("agent_args", agentVars);
		vars.put("config_path", configPath);
		return renderTemplate(config.hpc.submitTemplate, vars);
	}

	@Override
	public LaunchResult[] launchAgents(RemoteShell shell, Request[] requests) throws IOException {
		LaunchResult[] lr = new LaunchResult[requests.length];
		List<Request> _requests = applyFullCap(Arrays.asList(requests), lr);

		/* Calculate each batch. */
		TempBatch[] batches = calculateBatches(_requests);

		/* Now do things that can actually fail. */
		Instant utcNow = Instant.now();

		for(TempBatch tb : batches) {
			RemoteShell.CommandResult cr;

			cr = shell.runCommand("mkdir", "-p", tb.batchDir);
			if(cr.status != 0) {
				throw new IOException("Unable to create batch directory");
			}

			shell.upload(tb.scriptPath, tb.script.getBytes(StandardCharsets.UTF_8), EnumSet.of(PosixFilePermission.OWNER_READ), utcNow);

			for(int i = 0; i < tb.uuids.length; ++i) {
				shell.upload(tb.configPath[i], tb.config[i].toString().getBytes(StandardCharsets.UTF_8), EnumSet.of(PosixFilePermission.OWNER_READ), utcNow);
				remoteFiles.add(tb.configPath[i]);
			}

			String jobId;
			try {
				jobId = submitBatch(shell, tb);
			} catch(IOException e) {
				Arrays.fill(lr, tb.from, tb.to, new LaunchResult(null, e));
				return lr;
			}

			Batch b = new Batch(jobId, tb.batchUuid, tb.batchDir, tb.to - tb.from, tb.script, tb.scriptPath);
			Arrays.setAll(b.uuids, i -> tb.uuids[i]);
			for(int i = 0; i < b.results.length; ++i) {
				b.results[i] = new LaunchResult(node, null, null, Json.createObjectBuilder()
						.add("batch_id", b.jobId)
						.add("batch_uuid", b.uuid.toString())
						.add("batch_dir", b.batchDir)
						.add("batch_size", b.results.length)
						.add("batch_index", i)
						.add("script", tb.script)
						.add("script_path", tb.scriptPath)
						.add("config_path", tb.configPath[i])
						.build());

				lr[tb.from + i] = b.results[i];
				b.indexMap.put(b.uuids[i], i);
			}

			Arrays.setAll(b.configPath, i -> tb.configPath[i]);
			for(int i = 0; i < b.statuses.length(); ++i) {
				b.statuses.setOpaque(i, AgentStatus.Launching);
			}
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
		if(jobs.length == 0) {
			return;
		}

		killJobs(shell, jobs);

		for(UUID u : uuids) {
			notifyAgentDisconnection(u);
		}
	}

	/**
	 * @param shell  The remote shell to use.
	 * @param jobIds A list of batch system job identifiers to kill. This will never be empty.
	 * @return If all of the jobs were able to be killed, returns true. Otherwise, false.
	 */
	private boolean killJobs(RemoteShell shell, String[] jobIds) {
		try {
			config.hpc.deleteForce.execute(shell, jobIds);
		} catch(IOException ex) {
			if(LOGGER.isWarnEnabled()) {
				LOGGER.warn(String.format("Unable to kill jobs '%s'", String.join(",", jobIds)), ex);
			}
			return false;
		}
		return true;
	}

	@Override
	protected final void close(RemoteShell shell) {
		/* Doesn't really matter if this fails. */
		ses.shutdownNow();

		/*
		 * Attempt to cleanup the config files as they may contain secrets.
		 * Leave the rest for easier debugging.
		 */
		String[] args = Stream.concat(Stream.of("rm", "-f"), remoteFiles.stream()).toArray(String[]::new);

		try {
			shell.runCommand(args);
		} catch(IOException e) {
			LOGGER.warn("Unable to remove configuration files", e);
		}

		remoteFiles.clear();

		String[] jobs = jobNames.values().stream().map(b -> b.jobId).distinct().toArray(String[]::new);
		if(jobs.length > 0)
			killJobs(shell, jobs);
	}


	private static int findBatchIndex(Batch b, UUID uuid) {
		return b.indexMap.getOrDefault(uuid, -1);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		if(isClosed) {
			return;
		}

		Batch b = jobNames.get(state.getUUID());
		if(b == null) {
			return;
		}

		int i = findBatchIndex(b, state.getUUID());
		if(i < 0 || i >= b.uuids.length) {
			return;
		}
		b.connectState[i].setOpaque(CSTATE_CONNECTED);
		b.statuses.setOpaque(i, AgentStatus.Connected);

		/* Set the walltime. */
		state.setExpiryTime(state.getConnectionTime().plusSeconds(config.walltime));
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		if(isClosed) {
			return;
		}

		/*
		 * Agent gone, remove its name. Update the state too as the batch
		 * may still be referenced in the query thread.
		 */
		Batch b = jobNames.remove(uuid);
		if(b == null) {
			return;
		}

		int i = findBatchIndex(b, uuid);
		if(i < 0 || i >= b.uuids.length) {
			return;
		}
		b.connectState[i].setOpaque(CSTATE_DISCONNECTED);
		b.statuses.setOpaque(i, AgentStatus.Disconnected);
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

		JsonString jscript = data.getJsonString("script");
		if(jscript == null) {
			return AdoptStatus.Rejected;
		}

		JsonString jscriptpath = data.getJsonString("script_path");
		if(jscriptpath == null) {
			return AdoptStatus.Rejected;
		}

		JsonString jconfigpath = data.getJsonString("config_path");
		if(jconfigpath == null) {
			return AdoptStatus.Rejected;
		}

		String batchId = jbatchid.getString();
		String batchDir = jbatchdir.getString();
		int batchSize = jbatchsize.intValue();
		int batchIndex = jbatchindex.intValue();
		String script = jscript.getString();
		String scriptPath = jscriptpath.getString();
		String configPath = jconfigpath.getString();

		Batch batch = jobNames.get(state.getUUID());
		if(batch == null) {
			batch = new Batch(batchId, batchUuid, batchDir, batchSize, script, scriptPath);
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
		batch.configPath[batchIndex] = configPath;
		jobNames.putIfAbsent(state.getUUID(), batch);
		remoteFiles.add(configPath);

		return AdoptStatus.Adopted;
	}

	@Override
	public final boolean canSpawnAgents(int num) throws IllegalArgumentException {
		if(isClosed) {
			return false;
		}

		return jobNames.size() + num <= config.limit;
	}

	private static Actuator.AgentStatus mapAgentStatus(int cstate, Actuator.AgentStatus new_) {
		if(new_ == AgentStatus.Unknown) {
			return new_;
		}

		if(cstate == CSTATE_NONE) {
			/* Agent hasn't connected yet. */
			if(new_ == AgentStatus.Connected || new_ == AgentStatus.Disconnected) {
				/* invalid */
				return AgentStatus.Unknown;
			}
			return new_;
		} else if(cstate == CSTATE_CONNECTED) {
			/* Agent has connected. */
			if(new_ == AgentStatus.Launching) {
				/* invalid */
				return AgentStatus.Unknown;
			}

			if(new_ == AgentStatus.Launched) {
				return AgentStatus.Connected;
			}

			if(new_ == AgentStatus.Disconnected) {
				return AgentStatus.Dead;
			}

			return new_;
		} else if(cstate == CSTATE_DISCONNECTED) {
			/* Agent has disconnected. */
			if(new_ == AgentStatus.Launching || new_ == AgentStatus.Launched || new_ == AgentStatus.Connected) {
				/* invalid. */
				return AgentStatus.Unknown;
			}

			if(new_ == AgentStatus.Dead) {
				return AgentStatus.Disconnected;
			}

			return new_;
		}
		return AgentStatus.Unknown;
	}

	private void pollThread() {
		if(isClosed) {
			return;
		}

		/*
		 * Keep a snapshot of the batches. It doesn't really matter if
		 * a batch is deleted from underneath us.
		 */
		LinkedHashMap<String, Batch> batches = new LinkedHashMap<>(jobNames.size());

		if(jobNames.isEmpty()) {
			return;
		}

		jobNames.forEachValue(Integer.MAX_VALUE, v -> batches.put(v.jobId, v));

		QueryCommand.Response r;
		try(RemoteShell shell = makeClient()) {
			r = config.hpc.query.execute(shell, batches.keySet().toArray(String[]::new));
		} catch(IOException e) {
			LOGGER.error("Unable to query job status", e);
			return;
		}

		batches.forEach((job, batch) -> {
			QueryCommand.JobQueryInfo qi = r.jobInfo.get(job);
			assert qi != null;

			for(int i = 0; i < batch.statuses.length(); ++i) {
				Actuator.AgentStatus newStatus = mapAgentStatus(batch.connectState[i].getOpaque(), qi.status);
				Actuator.AgentStatus oldStatus = batch.statuses.getAndSet(i, newStatus);
				LOGGER.trace("Updating job {}, agent {} status from {} to {}", qi.jobId, batch.uuids[i], oldStatus, newStatus);
			}
		});
	}

	@Override
	public final AgentStatus queryStatus(RemoteShell shell, UUID uuid) {
		Batch b = jobNames.get(uuid);
		if(b == null) {
			return AgentStatus.Unknown;
		}

		int i = findBatchIndex(b, uuid);
		if(i < 0 || i >= b.uuids.length) {
			return AgentStatus.Unknown;
		}

		return b.statuses.getOpaque(i);
	}
}
