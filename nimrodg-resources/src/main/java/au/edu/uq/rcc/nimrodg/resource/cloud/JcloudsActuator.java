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
package au.edu.uq.rcc.nimrodg.resource.cloud;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.MachinePair;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcloudsActuator implements Actuator {

	private static final Logger LOGGER = LoggerFactory.getLogger(JcloudsActuator.class);

	private final Operations ops;
	private final Resource node;
	private final NimrodURI amqpUri;
	private final Certificate[] certs;

	private final AgentDefinition agentDef;
	private final int agentsPerNode;
	private final String tmpDir;
	private final CloudConfig config;

	private final ComputeService compute;
	private final Template template;
	private final String groupName;
	private final Map<NodeMetadata, NodeInfo> nodes;
	private final Map<UUID, NodeInfo> agentMap;
	private final SubOptions subOpts;
	private boolean isClosed;

	public static class CloudConfig {

		public final String contextName;
		public final URI endpoint;
		public final String locationId;
		public final String hardwareId;
		public final String imageId;
		public final String username;
		public final String password;
		public final String availabilityZone;
		public final Properties props;

		public CloudConfig(String contextName, URI endpoint, String locationId, String hardwareId, String imageId, String username, String password, String availabilityZone, Properties props) {
			this.contextName = contextName;
			this.endpoint = endpoint;
			this.locationId = locationId;
			this.hardwareId = hardwareId;
			this.imageId = imageId;
			this.username = username;
			this.password = password;
			this.availabilityZone = availabilityZone;
			this.props = props;
		}

	}

	public JcloudsActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, AgentDefinition agentDefinition, int agentsPerNode, String tmpDir, CloudConfig config) {
		this.ops = ops;
		this.node = node;
		this.amqpUri = amqpUri;
		this.certs = certs;
		this.agentDef = agentDefinition;
		this.agentsPerNode = agentsPerNode;
		this.tmpDir = tmpDir;
		this.config = config;

		this.compute = createComputeService();

		TemplateOptions opts = NovaTemplateOptions.Builder
				.availabilityZone(config.availabilityZone)
				.generateKeyPair(true)
				.inboundPorts(22)
				.blockOnComplete(false)
				.blockUntilRunning(false);

		this.template = compute.templateBuilder()
				.locationId(config.locationId)
				.imageId(config.imageId)
				.hardwareId(config.hardwareId)
				.options(opts)
				.build();

		this.groupName = String.format("nimrodg-jclouds-%d", this.hashCode());
		this.nodes = new HashMap<>();
		this.agentMap = new HashMap<>();
		this.subOpts = new SubOptions();
		this.isClosed = false;
	}

	private ComputeService createComputeService() {
		return ContextBuilder.newBuilder(config.contextName)
				.endpoint(config.endpoint.toString())
				.credentials(config.username, config.password)
				.overrides(config.props)
				.buildView(ComputeServiceContext.class)
				.getComputeService();
	}

	@Override
	public Resource getResource() {
		return node;
	}


	/* Launch agents on a given actuator, remapping the launch results to the correct index. */
	private static void launchAgentsOnActuator(LaunchResult[] results, Actuator act, Map<Request, Integer> indexMap, Set<Request> requests) {
		final Request[] _requests = requests.stream().toArray(Request[]::new);

		LaunchResult[] lrs;
		try {
			lrs = act.launchAgents(_requests);
		} catch(Throwable t) {
			lrs = new LaunchResult[_requests.length];
			Arrays.fill(lrs, new LaunchResult(act.getResource(), t));
		}

		for(int i = 0; i < _requests.length; ++i) {
			results[indexMap.get(_requests[i])] = lrs[i];
		}
	}

	private static <T> T waitUninterruptibly(CompletableFuture<T> cf, Function<ExecutionException, T> handler) {
		while(!cf.isDone()) {
			try {
				return cf.get();
			} catch(ExecutionException e) {
				return handler.apply(e);
			} catch(InterruptedException e) {
				/* nop */
			}
		}

		throw new IllegalStateException();
	}

	private static void launchNodes(ComputeService compute, int num, String groupName, Template template, AgentDefinition agentInfo, Map<NodeMetadata, NodeInfo> outGood, Map<NodeMetadata, Throwable> outBad) {
		/* These need to be mutable. */
		Set<NodeMetadata> good = new HashSet<>(num);
		Map<NodeMetadata, Throwable> bad = new HashMap<>(num);
		try {
			good.addAll(compute.createNodesInGroup(groupName, num, template));
		} catch(RunNodesException e) {
			/* The documentation is a bit unclear on this. */
			good.addAll(e.getSuccessfulNodes());
			bad.putAll(e.getNodeErrors());
		}

		Map<NodeMetadata, NodeInfo> nodes = new HashMap<>(good.size());
		for(NodeMetadata n : good) {
			try {
				nodes.put(n, NodeInfo.configure(n));
			} catch(RuntimeException e) {
				bad.put(n, e);
			}
		}
		good.removeAll(bad.keySet());

		outGood.putAll(nodes);
		outBad.putAll(bad);

		/* Attempt to resolve the host keys. */
		nodes.values().forEach(ni -> {
			ni.sshConfig = CompletableFuture.supplyAsync(() -> resolveTransportFromNode(ni, agentInfo));
		});
	}

	@Override
	public LaunchResult[] launchAgents(Request... requests) {

		/* See if there's any space left on our existing nodes. */
		LinkedHashSet<NodeInfo> good = nodes.values().stream()
				.filter(ni -> ni.agents.size() < agentsPerNode)
				.sorted(Comparator.comparingInt(a -> a.agents.size()))
				.collect(Collectors.toCollection(LinkedHashSet::new));

		/* Get the number of agents we can launch on our existing nodes. */
		int nLaunchable = good.stream()
				.map(ni -> agentsPerNode - ni.agents.size())
				.reduce(Integer::sum).orElse(0);

		// numRequired = ceil((uuids.length - nLaunchable) / agentsPerNode)
		int numRequired = ((requests.length - nLaunchable) / agentsPerNode)
				+ ((requests.length - nLaunchable) % agentsPerNode > 0 ? 1 : 0);

		ConcurrentHashMap<NodeMetadata, Throwable> bad = new ConcurrentHashMap<>(numRequired);

		if(nLaunchable < requests.length) {
			Map<NodeMetadata, NodeInfo> newNodes = new HashMap<>(numRequired);
			launchNodes(compute, numRequired, groupName, template, agentDef, newNodes, bad);

			/* Launch the actuator. */
			newNodes.values().forEach(ni -> ni.actuator = ni.sshConfig.thenApplyAsync(this::launchActuator));

			nodes.putAll(newNodes);
			good.addAll(newNodes.values());
		}

		CompletableFuture[] actFutures = good.stream()
				.map(ni -> ni.actuator)
				.toArray(CompletableFuture[]::new);

		/*
		 * Wait for the actuators to be created. This is uninterruptible, as each future
		 * should fail-fast interrupted and complete this future.
		 */
		waitUninterruptibly(CompletableFuture.allOf(actFutures), e -> {
			return null;
		});

		{
			/* NB: LinkedHashSet, iteration order is consistent. */
			int i = 0;
			for(NodeInfo ni : good) {
				assert actFutures[i].isDone();
				try {
					actFutures[i].join();
				} catch(CompletionException e) {
					bad.put(ni.node, e.getCause());
				} catch(CancellationException e) {
					bad.put(ni.node, e);
				}
				++i;
			}
		}

		/* Destroy the bad nodes. */
		if(!bad.isEmpty()) {
			try {
				compute.destroyNodesMatching(bad::containsKey);
			} catch(RuntimeException e) {
				LOGGER.warn("Caught exception during bad node cleanup.", e);
			}

			/* If an actuator launch failed, the node is now considered bad. */
			good.removeIf(ni -> bad.containsKey(ni.node));

			/* Now it's safe to remove them from our global list. */
			bad.keySet().forEach(nodes::remove);
		}

		FilterResult fr = FilterResult.filterRequestsToNodes(
				requests,
				Collections.unmodifiableSet(good),
				Collections.unmodifiableSet(bad.keySet()),
				agentsPerNode
		);

		LaunchResult[] results = new LaunchResult[requests.length];

		/* Failed agents are low-hanging fruit, do them first. */
		for(Map.Entry<NodeMetadata, Set<Actuator.Request>> e : fr.toFail.entrySet()) {
			Throwable t = bad.get(e.getKey());
			e.getValue().stream()
					.map(fr.indexMap::get)
					.forEach(i -> results[i] = new LaunchResult(node, t));
		}

		LaunchResult fullResult = new LaunchResult(null, new NimrodException.ResourceFull(node));
		fr.leftovers.stream()
				.map(fr.indexMap::get)
				.forEach(i -> results[i] = fullResult);

		actFutures = fr.toLaunch.entrySet().stream()
				.map(e -> e.getKey().actuator.thenAcceptAsync(act -> launchAgentsOnActuator(results, act, fr.indexMap, e.getValue())))
				.toArray(CompletableFuture[]::new);

		/* Again, should fail-fast. */
		waitUninterruptibly(CompletableFuture.allOf(actFutures), e -> {
			LOGGER.error("BUG! launchAgentsOnActuator() should never fail exceptionally", e);
			return null;
		});

		/* This *should* never happen, but double check to be safe. */
		for(int i = 0; i < results.length; ++i) {
			if(results[i] == null) {
				results[i] = fullResult;
			}
		}

		/* Override the actuator data with ours. */
		patchLaunchResults(groupName, fr.uuidMap, requests, results);

		for(int i = 0; i < results.length; ++i) {
			if(results[i].node != null) {
				agentMap.put(requests[i].uuid, fr.uuidMap.get(requests[i]));
			}
		}

		return results;
	}

	private void patchLaunchResults(String groupName, Map<Request, NodeInfo> uuidMap, Request[] requests, LaunchResult[] results) {
		assert requests.length == results.length;

		/* Override the actuator data with ours. */
		for(int i = 0; i < results.length; ++i) {
			LaunchResult old = results[i];
			if(old.t != null) {
				continue;
			}

			NodeInfo ni = uuidMap.get(requests[i]);
			if(ni == null) {
				continue;
			}

			JsonObjectBuilder jb = Json.createObjectBuilder()
					.add("group_name", groupName);

			JsonObjectBuilder nb = Json.createObjectBuilder();

			nb.add("id", ni.node.getId());

			Optional.ofNullable(ni.node.getGroup())
					.ifPresentOrElse(g -> nb.add("group", g), () -> nb.add("group", JsonValue.NULL));

			Optional.ofNullable(ni.node.getImageId())
					.ifPresentOrElse(id -> nb.add("image_id", id), () -> nb.add("image_id", JsonValue.NULL));

			Optional.ofNullable(ni.node.getHardware())
					.map(ComputeMetadata::getId)
					.ifPresentOrElse(h -> nb.add("hardware_id", h), () -> nb.add("hardware_id", JsonValue.NULL));

			/*
			 * Add the transport configuration.
			 * We know it's built at this point, so the getNow() call will never fail.
			 */
			nb.add("transport",
					Optional.ofNullable(ni.sshConfig.getNow(null))
							.map(cfg -> cfg.transportFactory.buildJsonConfiguration(cfg.transportConfig))
							.orElse(JsonObject.EMPTY_JSON_OBJECT)
			);

			JsonArrayBuilder urib = Json.createArrayBuilder();
			ni.uris.stream()
					.map(URI::toString)
					.forEach(urib::add);
			nb.add("uris", urib);
			nb.add("private_key", writeToPem(ni.keyPair.getPrivate()));

			jb.add("node", nb);

			/* Keep a copy of the old one. */
			if(old.actuatorData == null) {
				jb.add("remote", JsonValue.EMPTY_JSON_OBJECT);
			} else {
				jb.add("remote", old.actuatorData);
			}

			results[i] = new LaunchResult(old.node, old.t, old.expiryTime, jb.build());
		}
	}

	public static String writeToPem(PrivateKey pk) {
		StringWriter sw = new StringWriter();
		try(JcaPEMWriter pemw = new JcaPEMWriter(sw)) {
			pemw.writeObject(pk);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}

		return sw.toString();
	}

	@Override
	public void forceTerminateAgent(UUID[] uuids) {
		Map<NodeInfo, List<UUID>> nodeMap = NimrodUtils.mapToParent(
				Arrays.stream(uuids).filter(agentMap::containsKey),
				agentMap::remove
		);
		nodeMap.forEach((ni, uu) -> ni.agents.removeAll(uu));

		for(NodeInfo ni : nodeMap.keySet()) {
			UUID[] uuu = nodeMap.get(ni).stream().toArray(UUID[]::new);
			CompletableFuture<Void> cf = ni.actuator.thenAccept(act -> act.forceTerminateAgent(uuu));
			cf.exceptionally(t -> {
				LOGGER.error("Unable to terminate agents on node {}", ni.node.getId(), t);
				return null;
			});
		}
	}

	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	@Override
	public void close() throws IOException {
		IOException ioex = new IOException();

		AtomicInteger numSuppressed = new AtomicInteger(0);

		CompletableFuture<Void> cf = CompletableFuture.allOf(nodes.values().stream().map(ni -> ni.actuator.thenAcceptAsync(act -> {
			ni.agents.clear();

			try {
				act.close();
			} catch(RuntimeException | IOException e) {
				ioex.addSuppressed(e);
				numSuppressed.incrementAndGet();
			}
		})).toArray(CompletableFuture[]::new));

		while(!cf.isDone()) {
			try {
				cf.get();
			} catch(ExecutionException e) {
				ioex.addSuppressed(e);
				numSuppressed.incrementAndGet();
				break;
			} catch(InterruptedException e) {
				/* nop */
			}
		}

		try {
			compute.destroyNodesMatching(n -> groupName.equals(n.getGroup()));
		} catch(RuntimeException e) {
			ioex.addSuppressed(e);
			numSuppressed.incrementAndGet();
		}

		if(numSuppressed.get() != 0) {
			isClosed = true;
			throw ioex;
		}
		isClosed = true;
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		/* nop */
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		NodeInfo ni = agentMap.remove(uuid);
		if(ni == null) {
			return;
		}

		ni.agents.remove(uuid);
	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		//nodes.values().stream().map(n -> n.agents.size()).reduce(Integer::sum);
		return true;
	}

	@Override
	public Actuator.AdoptStatus adopt(AgentState state) {
		JsonObject jo = state.getActuatorData();
		if(jo == null) {
			return AdoptStatus.Rejected;
		}

		if(state.getState() == AgentInfo.State.SHUTDOWN) {
			return AdoptStatus.Rejected;
		}

		JsonObject no = jo.getJsonObject("node");
		if(no == null) {
			return AdoptStatus.Rejected;
		}

		Optional<String> nodeId = Optional.ofNullable(no.get("id"))
				.filter(j -> j.getValueType() == JsonValue.ValueType.STRING)
				.map(j -> ((JsonString)j).getString());

		if(nodeId.isEmpty()) {
			return AdoptStatus.Rejected;
		}

		Optional<String> _privateKey = Optional.ofNullable(no.get("private_key"))
				.filter(j -> j.getValueType() == JsonValue.ValueType.STRING)
				.map(j -> ((JsonString)j).getString());

		if(_privateKey.isEmpty()) {
			return AdoptStatus.Rejected;
		}

		KeyPair keyPair;

		try {
			keyPair = ActuatorUtils.readPEMKey(_privateKey.get());
		} catch(IOException e) {
			LOGGER.warn("Unable to adopt agent {}, unable to parse private key.", state.getUUID(), e);
			return AdoptStatus.Rejected;
		}

		CloudSshFactory factory = new CloudSshFactory(keyPair);

		Optional<NodeMetadata> n = nodes.keySet().stream()
				.filter(nn -> nn.getId().equals(nodeId.get()))
				.findFirst();

		NodeInfo ni;
		if(n.isEmpty()) {
			/* We don't know about the node, try to reconstruct it. */
			n = Optional.ofNullable(compute.getNodeMetadata(nodeId.get()));
			if(n.isEmpty()) {
				return AdoptStatus.Rejected;
			}

			Optional<TransportFactory.Config> tcfg = Optional.ofNullable(no.getJsonObject("transport"))
					.flatMap(cfg -> factory.validateConfiguration(cfg, new ArrayList<>()));

			if(tcfg.isEmpty()) {
				return AdoptStatus.Rejected;
			}

			ni = NodeInfo.recover(
					n.get(),
					no.getJsonArray("uris").stream()
							.map(j -> URI.create(((JsonString)j).getString()))
							.toArray(URI[]::new),
					new SSHResourceType.SSHConfig(agentDef, factory, tcfg.get(), List.of()),
					keyPair
			);
		} else {
			ni = nodes.get(n.get());
		}

		if(!ni.sshConfig.isDone()) {
			/* Should never happen. */
			return AdoptStatus.Rejected;
		}

		SSHResourceType.SSHConfig sscfg = ni.sshConfig.getNow(null);

		/* Complete the actuator future if not already done so. */
		if(!ni.actuator.isDone()) {
			RemoteActuator act;
			try {
				act = launchActuator(sscfg);
			} catch(UncheckedIOException e) {
				ni.actuator.completeExceptionally(e);
				LOGGER.error("Unable to launch actuator.", e);
				return AdoptStatus.Rejected;
			}

			ni.actuator.complete(act);
		}

		nodes.putIfAbsent(ni.node, ni);

		JsonObject ro = jo.getJsonObject("remote");
		if(ro == null) {
			return AdoptStatus.Rejected;
		}

		AgentState as = new DefaultAgentState(state);
		as.setActuatorData(ro);

		try {
			return ni.actuator.thenApply(act -> act.adopt(as)).get();
		} catch(InterruptedException | ExecutionException e) {
			LOGGER.error("Unable to ask nested actuator to adopt agent.");
			return AdoptStatus.Rejected;
		}
	}

	@Override
	public AgentStatus queryStatus(UUID uuid) {
		/* FIXME: */
		return AgentStatus.Unknown;
	}

	private static SSHResourceType.SSHConfig resolveTransportFromNode(NodeInfo ni, AgentDefinition agentDef) {
		URI uri = ni.uris.get(0); // FIXME:

		/* FIXME: Make this configurable. Currently 18 retries at 10 seconds, or 3 minutes. */
		PublicKey[] hostKeys;
		try {
			hostKeys = SshdClient.resolveHostKeys(ShellUtils.getUriUser(uri).orElse(""), uri.getHost(), uri.getPort(), 18, 10000);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		return new SSHResourceType.SSHConfig(
				agentDef,
				new CloudSshFactory(ni.keyPair),
				new TransportFactory.Config(
						Optional.of(uri),
						hostKeys,
						Optional.empty(),
						Optional.empty()
				),
				List.of()
		);
	}

	/* Helper for use in lambdas. */
	private RemoteActuator launchActuator(SSHResourceType.SSHConfig sscfg) {
		try {
			return new RemoteActuator(subOpts, node, amqpUri, certs, agentsPerNode, tmpDir, sscfg);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private class SubOptions implements Actuator.Operations {

		@Override
		public void reportAgentFailure(Actuator act, UUID uuid, AgentInfo.ShutdownReason reason, int signal) throws IllegalArgumentException {
			/* Rewrite nested failures as ours. */
			JcloudsActuator.this.ops.reportAgentFailure(JcloudsActuator.this, uuid, reason, signal);
		}

		@Override
		public NimrodConfig getConfig() {
			return JcloudsActuator.this.ops.getConfig();
		}

		@Override
		public String getSigningAlgorithm() {
			return JcloudsActuator.this.ops.getSigningAlgorithm();
		}

		@Override
		public int getAgentCount(Resource res) {
			/* FIXME: */
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, AgentDefinition> lookupAgents() {
			return JcloudsActuator.this.ops.lookupAgents();
		}

		@Override
		public AgentDefinition lookupAgentByPlatform(String platString) {
			return JcloudsActuator.this.ops.lookupAgentByPlatform(platString);
		}

		@Override
		public AgentDefinition lookupAgentByPosix(MachinePair pair) {
			return JcloudsActuator.this.ops.lookupAgentByPosix(pair);
		}
	}

}
