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
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;

public class HPCActuator extends POSIXActuator<HPCConfig> {

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

	protected static final class Batch {
		final String jobId;
		final UUID uuid;
		final String batchDir;
		final LaunchResult[] results;
		final UUID[] uuids;
		final AgentStatus[] statuses;
		final String script;
		final String scriptPath;
		final String[] configPath;

		Batch(String jobId, UUID uuid, String batchDir, int size, String script, String scriptPath) {
			this.jobId = jobId;
			this.uuid = uuid;
			this.batchDir = batchDir;
			this.results = new LaunchResult[size];
			this.uuids = new UUID[size];
			this.statuses = new AgentStatus[size];
			this.script = script;
			this.scriptPath = scriptPath;
			this.configPath = new String[size];
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(POSIXActuator.class);

	private final ConcurrentHashMap<UUID, Batch> jobNames;
	protected final JsonObject baseConfig;

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
				ActuatorUtils.resolveEnvironment(cfg.forwardedEnvironment)
		).build();

		this.remoteFiles = new HashSet<>();
	}

	private String submitBatch(RemoteShell shell, TempBatch batch) throws IOException {
		return config.hpc.submit.execute(shell, batch.scriptPath).jobId;
	}

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

	private TempBatch buildBatch(UUID batchUuid, List<UUID> uuids, int startIndex) {
		String batchDir = ActuatorUtils.posixJoinPaths(nimrodHomeDir, "batch-" + batchUuid.toString());

		String[] configPath = uuids.stream()
				.map(u -> ActuatorUtils.posixJoinPaths(batchDir, "config-" + u.toString() + ".json"))
				.toArray(String[]::new);

		JsonObject[] config = uuids.stream()
				.map(u -> Json.createObjectBuilder(baseConfig).add("uuid", u.toString()).build())
				.toArray(JsonObject[]::new);

		String stdout = ActuatorUtils.posixJoinPaths(batchDir, "stdout.txt");
		String stderr = ActuatorUtils.posixJoinPaths(batchDir, "stderr.txt");
		String pbsPath = ActuatorUtils.posixJoinPaths(batchDir, "job.sh");
		String pbsScript = buildSubmissionScript(batchUuid, uuids, configPath, stdout, stderr);

		/* TODO/PERF: Remove the extra array allocation here. */
		return new TempBatch(batchUuid, batchDir, pbsPath, pbsScript, stdout, stderr,
				startIndex, startIndex + uuids.size(), uuids.stream().toArray(UUID[]::new), configPath, config);
	}

	/**
	 * Given a list of agent UUIDs, generate a list of batches to submit.
	 *
	 * @param uuids The list of agent UUIDs.
	 * @return A list of batches to submit.
	 */
	protected TempBatch[] calculateBatches(List<UUID> uuids) {
		int nBatches = (uuids.size() / config.maxBatchSize) + ((uuids.size() % config.maxBatchSize) >= 1 ? 1 : 0);
		TempBatch[] batches = new TempBatch[nBatches];

		for(int i = 0, launchIndex = 0; i < nBatches && launchIndex < uuids.size(); ++i) {
			int batchSize = Math.min(config.maxBatchSize, uuids.size() - launchIndex);
			batches[i] = buildBatch(UUID.randomUUID(), uuids.subList(launchIndex, launchIndex + batchSize), launchIndex);
			launchIndex += batchSize;
		}
		return batches;
	}

	private String buildSubmissionScript(UUID batchUuid, List<UUID> agentUuids, String[] configPath, String out, String err) {
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
		vars.put("batch_size", agentUuids.size());
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
		UUID[] uuids = Arrays.stream(requests)
				.map(r -> r.uuid)
				.toArray(UUID[]::new);
		uuids = applyFullCap(uuids, lr);
		/* Calculate each batch. */
		TempBatch[] batches = calculateBatches(Arrays.asList(uuids));

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
				LaunchResult res = new LaunchResult(null, e);
				for(int i = tb.from; i < uuids.length; ++i) {
					lr[i] = res;
				}

				return lr;
			}

			Batch b = new Batch(jobId, tb.batchUuid, tb.batchDir, tb.to - tb.from, tb.script, tb.scriptPath);
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
			}
			Arrays.setAll(b.uuids, i -> tb.uuids[i]);
			Arrays.setAll(b.configPath, i -> tb.configPath[i]);
			Arrays.fill(b.statuses, AgentStatus.Launching);
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
		int i;
		for(i = 0; i < b.uuids.length; ++i) {
			if(b.uuids[i] == uuid) {
				return i;
			}
		}

		return -1;
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
		b.statuses[i] = AgentStatus.Connected;

		/* Set the walltime. */
		state.setExpiryTime(state.getConnectionTime().plusSeconds(config.walltime));
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

	private AgentStatus queryStatus(RemoteShell shell, UUID uuid, String jobId) throws IOException {
		QueryCommand.Response r = config.hpc.query.execute(shell, new String[]{jobId});

		QueryCommand.JobQueryInfo jqi = r.jobInfo.get(jobId);
		if(jqi == null) {
			return AgentStatus.Unknown;
		}

		return jqi.status;
	}

	@Override
	public final AgentStatus queryStatus(RemoteShell shell, UUID uuid) {
		Batch b = jobNames.get(uuid);
		if(b == null) {
			return AgentStatus.Unknown;
		}

		return b.statuses[findBatchIndex(b, uuid)];
//		try {
//			return queryStatus(shell, uuid, b.jobId);
//		} catch(IOException e) {
//			LOGGER.error("Unable to query status of job {}", b.jobId, e);
//			return AgentStatus.Unknown;
//		}
	}
}
