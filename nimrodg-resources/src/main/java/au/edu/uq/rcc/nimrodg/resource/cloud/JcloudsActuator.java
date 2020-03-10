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

import au.edu.uq.rcc.nimrodg.agent.Agent;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.agent.DefaultAgentState;
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import au.edu.uq.rcc.nimrodg.resource.ssh.ClientFactories;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;
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

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
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

public class JcloudsActuator implements Actuator {

	private static final Logger LOGGER = LoggerFactory.getLogger(JcloudsActuator.class);

	private static final TransportFactory TRANSPORT_FACTORY = ClientFactories.SSHD_FACTORY;

	private final Operations ops;
	private final Resource node;
	private final NimrodURI amqpUri;
	private final Certificate[] certs;

	private final AgentInfo agentInfo;
	private final int agentsPerNode;
	private final String tmpDir;
	private final CloudConfig config;

	private final ComputeService compute;
	private final Template template;
	private final String groupName;
	private final Map<NodeMetadata, NodeInfo> nodes;
	private final Map<UUID, NodeInfo> agentMap;
	private final SubOptions subOpts;

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

	public JcloudsActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, AgentInfo agentInfo, int agentsPerNode, String tmpDir, CloudConfig config) {
		this.ops = ops;
		this.node = node;
		this.amqpUri = amqpUri;
		this.certs = certs;
		this.agentInfo = agentInfo;
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
	@SuppressWarnings("UseSpecificCatch")
	private static void launchAgentsOnActuator(LaunchResult[] results, Actuator act, Map<UUID, Integer> indexMap, Set<UUID> uuids) {
		final UUID[] _uuids = uuids.stream().toArray(UUID[]::new);

		LaunchResult[] lrs;
		try {
			lrs = act.launchAgents(_uuids);
		} catch(Throwable t) {
			lrs = new LaunchResult[_uuids.length];
			Arrays.fill(lrs, new LaunchResult(act.getResource(), t));
		}

		for(int i = 0; i < _uuids.length; ++i) {
			results[indexMap.get(_uuids[i])] = lrs[i];
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

	@SuppressWarnings("ThrowableResultIgnored")
	private static void launchNodes(ComputeService compute, int num, String groupName, Template template, AgentInfo agentInfo, Map<NodeMetadata, NodeInfo> outGood, Map<NodeMetadata, Throwable> outBad) {
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
		for(NodeInfo ni : nodes.values()) {
			ni.sshConfig = CompletableFuture.supplyAsync(() -> resolveTransportFromNode(ni, agentInfo));
		}
	}

	@Override
	@SuppressWarnings("ThrowableResultIgnored")
	/* NetBeans really overdoes this. */
	public LaunchResult[] launchAgents(UUID[] uuids) {

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
		int numRequired = ((uuids.length - nLaunchable) / agentsPerNode)
				+ ((uuids.length - nLaunchable) % agentsPerNode > 0 ? 1 : 0);

		ConcurrentHashMap<NodeMetadata, Throwable> bad = new ConcurrentHashMap<>(numRequired);

		if(nLaunchable < uuids.length) {
			Map<NodeMetadata, NodeInfo> newNodes = new HashMap<>(numRequired);
			launchNodes(compute, numRequired, groupName, template, agentInfo, newNodes, bad);

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
		 * should fail-fast if interrupted and complete this future.
		 */
		waitUninterruptibly(CompletableFuture.allOf(actFutures), e -> null);

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
			good.removeAll(NimrodUtils.mapToParent(bad.keySet(), nodes::get).keySet());

			/* Now it's safe to remove them from our global list. */
			bad.keySet().forEach(nodes::remove);
		}

		FilterResult fr = FilterResult.filterAgentsToNodes(uuids, good, bad.keySet(), agentsPerNode);

		LaunchResult[] results = new LaunchResult[uuids.length];

		/* Failed agents are low-hanging fruit, do them first. */
		for(Map.Entry<NodeMetadata, Set<UUID>> e : fr.toFail.entrySet()) {
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
			LOGGER.error("launchAgentsOnActuator() threw exception. This is a bug", e);
			return null;
		});

		/* This *should* never happen, but double check to be safe. */
		for(int i = 0; i < results.length; ++i) {
			if(results[i] == null) {
				results[i] = fullResult;
			}
		}

		/* Override the actuator data with ours. */
		patchLaunchResults(groupName, fr.uuidMap, uuids, results);

		for(int i = 0; i < results.length; ++i) {
			if(results[i].node != null) {
				agentMap.put(uuids[i], fr.uuidMap.get(uuids[i]));
			}
		}

		return results;
	}

	private static void patchLaunchResults(String groupName, Map<UUID, NodeInfo> uuidMap, UUID[] uuids, LaunchResult[] results) {
		assert uuids.length == results.length;

		/* Override the actuator data with ours. */
		for(int i = 0; i < results.length; ++i) {
			LaunchResult old = results[i];
			if(old.t != null) {
				continue;
			}

			NodeInfo ni = uuidMap.get(uuids[i]);
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
							.map(cfg -> TRANSPORT_FACTORY.buildJsonConfiguration(cfg.transportConfig))
							.orElse(JsonObject.EMPTY_JSON_OBJECT)
			);

			JsonArrayBuilder urib = Json.createArrayBuilder();
			ni.uris.stream()
					.map(URI::toString)
					.forEach(urib::add);
			nb.add("uris", urib);

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

	@Override
	public void forceTerminateAgent(UUID[] uuid) {
		Map<NodeInfo, List<UUID>> xxx = NimrodUtils.mapToParent(Arrays.stream(uuid), agentMap::get);

		/* Strip all agents that aren't ours. */
		xxx.remove(null);

		if(xxx.isEmpty()) {
			return;
		}

		xxx.forEach((ni, uu) -> ni.agents.removeAll(uu));

		CompletableFuture cf = CompletableFuture.allOf(xxx.entrySet().stream()
				.map(e -> e.getKey().actuator.thenAccept(act -> act.forceTerminateAgent(e.getValue().stream().toArray(UUID[]::new)))
						.handle((v, t) -> {
							if(t == null) {
								return null;
							}

							/* Swallow and log any exceptions that happen. */
							String uuidList = e.getValue().stream().map(UUID::toString).collect(Collectors.joining(", "));
							LOGGER.error(String.format("Unable to terminate agents %s", uuidList), t);
							return null;
						})
				)
				.toArray(CompletableFuture[]::new));

		/* FIXME: I don't like this... */
		while(!cf.isDone()) {
			try {
				cf.get();
			} catch(ExecutionException e) {
				LOGGER.warn("forceTerminateAgent() threw in sub-actuator. This is a bug.", e);
			} catch(InterruptedException e) {
				/* nop */
			}
		}
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
			throw ioex;
		}
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
	public AdoptStatus adopt(AgentState state) {
		JsonObject data = state.getActuatorData();
		if(data == null) {
			return AdoptStatus.Rejected;
		}

		if(state.getState() == Agent.State.SHUTDOWN) {
			return AdoptStatus.Rejected;
		}

		JsonObject no = data.getJsonObject("node");
		if(no == null) {
			return AdoptStatus.Rejected;
		}

		Optional<String> nodeId = Optional.ofNullable(no.get("id"))
				.filter(j -> j.getValueType() == JsonValue.ValueType.STRING)
				.map(j -> ((JsonString)j).getString());

		if(!nodeId.isPresent()) {
			return AdoptStatus.Rejected;
		}

		Optional<NodeMetadata> n = nodes.keySet().stream()
				.filter(nn -> nn.getId().equals(nodeId.get()))
				.findFirst();

		NodeInfo ni;
		if(!n.isPresent()) {
			/* We don't know about the node, try to reconstruct it. */
			n = Optional.of(compute.getNodeMetadata(nodeId.get()));
			if(!n.isPresent()) {
				return false;
			}

			Optional<TransportFactory.Config> tcfg = Optional.ofNullable(no.getJsonObject("transport"))
					.flatMap(cfg -> TRANSPORT_FACTORY.validateConfiguration(cfg, new ArrayList<>()));

			if(!tcfg.isPresent()) {
				return AdoptStatus.Rejected;
			}

			ni = NodeInfo.recover(
					n.get(),
					no.getJsonArray("uris").stream()
							.map(j -> URI.create(((JsonString)j).getString()))
							.toArray(URI[]::new),
					new SSHResourceType.SSHConfig(agentInfo, TRANSPORT_FACTORY, tcfg.get(), List.of())
			);
		} else {
			ni = nodes.get(n.get());
		}

		if(!ni.sshConfig.isDone()) {
			/* Should never happen. */
			return AdoptStatus.Rejected;
		}

		SSHResourceType.SSHConfig sscfg = ni.sshConfig.getNow(null);

		if(!ni.actuator.isDone()) {
			RemoteActuator act;
			try {
				act = launchActuator(sscfg);
			} catch(UncheckedIOException e) {
				ni.actuator.completeExceptionally(e);
				LOGGER.catching(e);
				return false;
			}

			ni.actuator.complete(act);
		}

		nodes.putIfAbsent(ni.node, ni);

		JsonObject ro = data.getJsonObject("remote");
		if(ro == null) {
			return false;
		}

		AgentState as = new DefaultAgentState(state);
		as.setActuatorData(ro);

		try {
			return ni.actuator.thenApply(act -> act.adopt(as)).get();
		} catch(InterruptedException | ExecutionException e) {
			LOGGER.catching(e);
			return false;
		}
	}

	private static SSHResourceType.SSHConfig resolveTransportFromNode(NodeInfo ni, AgentInfo agentInfo) {
		URI uri = ni.uris.get(0); // FIXME:

		/* FIXME: Make this configurable. Currently 18 retries at 10 seconds, or 3 minutes. */

		Optional<String> uriUser = ShellUtils.getUriUser(uri);

		PublicKey[] hostKeys;
		try {
			hostKeys = SshdClient.resolveHostKeys(uriUser.orElse(""), uri.getHost(), uri.getPort(), 18, 10000);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		return new SSHResourceType.SSHConfig(
				agentInfo,
				TRANSPORT_FACTORY,
				new TransportFactory.Config(
						Optional.of(uri),
						uriUser,
						hostKeys,
						ni.keyPair,
						Optional.empty()
				)
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
		public void reportAgentFailure(Actuator act, UUID uuid, AgentShutdown.Reason reason, int signal) throws IllegalArgumentException {
			JcloudsActuator.this.ops.reportAgentFailure(JcloudsActuator.this, uuid, reason, signal);
		}

		@Override
		public NimrodMasterAPI getNimrod() {
			return JcloudsActuator.this.ops.getNimrod();
		}
	}

}
