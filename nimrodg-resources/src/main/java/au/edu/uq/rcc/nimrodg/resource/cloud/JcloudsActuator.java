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
import au.edu.uq.rcc.nimrodg.agent.messages.AgentShutdown;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodMasterAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceFullException;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHClient;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;

public class JcloudsActuator implements Actuator {

	private static final Logger LOGGER = LogManager.getLogger(JcloudsActuator.class);

	private final Operations ops;
	private final NimrodAPI nimrod;
	private final Resource node;
	private final NimrodURI amqpUri;
	private final Certificate[] certs;

	private final int agentsPerNode;
	private final CloudConfig config;
	private final AgentInfo agentInfo;
	private final String tmpDir;

	private final Set<com.google.inject.Module> modules;
	private final ComputeService compute;
	private final Template template;
	private final String groupName;
	private final Map<NodeMetadata, NodeInfo> nodes;
	private final SubOptions subOpts;

	private static class NodeInfo {

		public final NodeMetadata node;
		public final Set<UUID> agents;
		public final CompletableFuture<RemoteActuator> actuator;

		private boolean isConfigured;
		private String username;
		private Optional<String> password;
		private Optional<KeyPair> keyPair;
		private List<URI> uris;

		public NodeInfo(NodeMetadata node) {
			this.node = node;
			this.agents = new HashSet<>();
			this.actuator = new CompletableFuture<>();

			this.isConfigured = false;
			this.username = null;
			this.password = Optional.empty();
			this.keyPair = Optional.empty();
			this.uris = List.of();
		}

		void configure(String username, Optional<String> password, Optional<KeyPair> keyPair) {
			if(isConfigured) {
				throw new IllegalStateException();
			}

			String userInfo = password.map(p -> String.format("%s:%s", username, p)).orElse(username);

			this.username = username;
			this.password = password;
			this.keyPair = keyPair;
			this.uris = node.getPublicAddresses().stream()
					.map(addr -> UriBuilder.fromUri("")
					.scheme("ssh")
					.host(addr)
					.userInfo(userInfo)
					.port(node.getLoginPort())
					.build()).collect(Collectors.toList());
			isConfigured = true;
		}

		public boolean isConfigured() {
			return this.isConfigured;
		}
	}

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

	public JcloudsActuator(Operations ops, NimrodAPI nimrod, Resource node, NimrodURI amqpUri, Certificate[] certs, int agentsPerNode, CloudConfig config) {
		this.ops = ops;
		this.nimrod = nimrod;
		this.node = node;
		this.amqpUri = amqpUri;
		this.certs = certs;
		this.agentsPerNode = agentsPerNode;
		this.config = config;

		// FIXME: make these configurable
		this.agentInfo = ops.getNimrod().lookupAgentByPlatform("x86_64-pc-linux-musl");
		this.tmpDir = "/tmp";

		this.modules = Set.of();
		/* Seems we don't need any. */
		this.compute = createComputeService();

		TemplateOptions opts = NovaTemplateOptions.Builder
				.availabilityZone("QRIScloud")
				.generateKeyPair(true)
				.inboundPorts(22)
				.userMetadata(Map.of(
						"availability_zone", config.availabilityZone,
						"OS-EXT-AZ:availability_zone", config.availabilityZone
				))
				.blockOnComplete(false)
				.blockUntilRunning(false);
		//.networks("283e92a3-40dc-482f-bb94-9f4632c0190b");

		this.template = compute.templateBuilder()
				.locationId(config.locationId)
				.imageId(config.imageId)
				.hardwareId(config.hardwareId)
				.options(opts)
				.build();

		this.groupName = String.format("nimrodg-openstack-%d", this.hashCode());
		this.nodes = new HashMap<>();
		this.subOpts = new SubOptions();
	}

	private ComputeService createComputeService() {
		return ContextBuilder.newBuilder(config.contextName)
				.endpoint(config.endpoint.toString())
				.credentials(config.username, config.password)
				.overrides(config.props)
				.modules(modules)
				.buildView(ComputeServiceContext.class)
				.getComputeService();
	}

	@Override
	public Resource getResource() throws NimrodAPIException {
		return node;
	}

	@Override
	public NimrodURI getAMQPUri() {
		return amqpUri;
	}

	@Override
	public Certificate[] getAMQPCertificates() {
		return Arrays.copyOf(certs, certs.length);
	}

	private static class FilterResult {

		public final Map<NodeInfo, Set<UUID>> toLaunch;
		public final Map<NodeMetadata, Set<UUID>> toFail;
		public final Set<UUID> leftovers;
		public final Map<UUID, Integer> indexMap;

		public FilterResult(Map<NodeInfo, Set<UUID>> toLaunch, Map<NodeMetadata, Set<UUID>> toFail, Set<UUID> leftovers, Map<UUID, Integer> indexMap) {
			this.toLaunch = toLaunch;
			this.toFail = toFail;
			this.leftovers = leftovers;
			this.indexMap = indexMap;
		}

	}

	/**
	 * Given a set of "good" and "bad" nodes, assign agents to them.
	 *
	 * @param uuids The agent UUIDs.
	 * @param good The set of "good" nodes.
	 * @param bad The set of "bad" nodes.
	 * @param agentsPerNode The number of agents allowed on a node.
	 */
	private static FilterResult filterAgentsToNodes(UUID[] uuids, Set<NodeInfo> good, Set<NodeMetadata> bad, int agentsPerNode) {
		Deque<NodeInfo> _good = new ArrayDeque<>(good);
		Deque<NodeMetadata> _bad = new ArrayDeque<>(bad);

		Map<NodeInfo, Set<UUID>> toLaunch = new HashMap<>();
		Map<NodeMetadata, Set<UUID>> toFail = new HashMap<>();

		int i = 0;
		while(!_good.isEmpty()) {
			if(i >= uuids.length) {
				break;
			}

			Set<UUID> agents = NimrodUtils.getOrAddLazy(toLaunch, _good.peek(), nii -> new HashSet<>());
			if(agents.size() >= agentsPerNode) {
				_good.poll();
				continue;
			}

			agents.add(uuids[i++]);
		}

		while(!_bad.isEmpty()) {
			if(i >= uuids.length) {
				break;
			}

			NimrodUtils.getOrAddLazy(toFail, _bad.poll(), nn -> new HashSet<>()).add(uuids[i++]);
		}

		Set<UUID> leftovers = new HashSet<>();
		for(int j = i; j < uuids.length; ++j) {
			leftovers.add(uuids[j]);
		}

		Map<UUID, Integer> indexMap = new HashMap<>();
		for(i = 0; i < uuids.length; ++i) {
			indexMap.put(uuids[i], i);
		}

		return new FilterResult(toLaunch, toFail, leftovers, indexMap);
	}

	/**
	 * Launch a {@link RemoteActuator} on the given node.
	 *
	 * This is intended to be used asynchronously.
	 *
	 * @param ni The node.
	 * @return The actuator instance.
	 */
	private RemoteActuator launchNodeActuator(NodeInfo ni) {
		URI uri = ni.uris.get(0); // FIXME:

		/* FIXME: Make this configurable. Currently 18 retries at 10 seconds, or 3 minutes. */
		PublicKey[] hostKeys;
		try {
			hostKeys = SSHClient.resolveHostKeys(ni.username, uri.getHost(), uri.getPort(), 18, 10000);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		SSHResourceType.SSHConfig sscfg = new SSHResourceType.SSHConfig(
				agentInfo,
				SSHClient.FACTORY,
				new TransportFactory.Config(
						Optional.of(uri),
						Optional.of(ni.username),
						hostKeys,
						Optional.empty(),
						ni.keyPair,
						Optional.empty()
				)
		);

		RemoteActuator act;
		try {
			act = new RemoteActuator(subOpts, node, amqpUri, certs, agentsPerNode, tmpDir, sscfg);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		return act;
	}

	@Override
	public LaunchResult[] launchAgents(UUID[] uuids) throws IOException {

		/* See if there's any space left on our existing nodes. */
		LinkedHashSet<NodeInfo> good = nodes.values().stream()
				.filter(ni -> ni.agents.size() < agentsPerNode)
				.sorted((a, b) -> Integer.compare(a.agents.size(), b.agents.size()))
				.collect(Collectors.toCollection(() -> new LinkedHashSet<>()));

		/* Get the number of agents we can launch on our existing nodes. */
		int nLaunchable = good.stream()
				.map(ni -> agentsPerNode - ni.agents.size())
				.reduce(Integer::sum).orElse(0);

		// numRequired = ceil((uuids.length - nLaunchable) / agentsPerNode)
		int numRequired = ((uuids.length - nLaunchable) / agentsPerNode)
				+ ((uuids.length - nLaunchable) % agentsPerNode > 0 ? 1 : 0);

		// FIXME: I don't like this
		ConcurrentHashMap<NodeMetadata, Throwable> bad = new ConcurrentHashMap<>();
		Set<NodeMetadata> _good = new HashSet<>();
		if(nLaunchable < uuids.length) {
			try {
				_good.addAll(compute.createNodesInGroup(groupName, numRequired, template));
			} catch(RunNodesException e) {
				/* The documentation is a bit unclear on this. */
				_good.addAll(e.getSuccessfulNodes());
				bad.putAll(e.getNodeErrors());
			}
		}

		/*
		 * Add all spawned nodes now, so we still know about them if something throws.
		 * I'm not confident enough in the validation code below to not do this.
		 */
		_good.forEach(n -> nodes.put(n, new NodeInfo(n)));

		/* Validate the credentials for each node, adding them to the bad list if anything failed. */
		for(NodeMetadata n : _good) {
			NodeInfo ni = nodes.get(n);
			if(ni.node.getPublicAddresses().isEmpty()) {
				bad.put(n, new RuntimeException("no public addresses"));
				continue;
			}

			Throwable t = configureNode(ni, n.getCredentials());
			if(t != null) {
				bad.put(n, t);
			} else {
				good.add(ni);
			}
		}

		CompletableFuture[] actFutures = good.stream()
				.map(ni -> CompletableFuture.supplyAsync(() -> launchNodeActuator(ni))
				.handle((act, t) -> {
					if(act != null) {
						ni.actuator.complete(act);
					} else {
						ni.actuator.completeExceptionally(t);
						bad.put(ni.node, t);
					}
					return null;
				})).toArray(CompletableFuture[]::new);

		try {
			CompletableFuture.allOf(actFutures).get();
		} catch(ExecutionException | InterruptedException e) {
			//int x = 0;
			LOGGER.catching(e);
		}

		/* Destroy the bad nodes. */
		compute.destroyNodesMatching(n -> bad.containsKey(n));

		/* If an actuator launch failed, the node is now considered bad. */
		good.removeAll(bad.keySet());

		/* Now it's safe to remove them from our global list. */
		bad.keySet().forEach(nodes::remove);

		FilterResult fr = filterAgentsToNodes(uuids, good, bad.keySet(), agentsPerNode);

		LaunchResult[] results = new LaunchResult[uuids.length];

		/* Failed agents are low-hanging fruit, do them first. */
		for(Map.Entry<NodeMetadata, Set<UUID>> failed : fr.toFail.entrySet()) {
			Throwable t = bad.get(failed.getKey());
			failed.getValue().stream()
					.map(u -> fr.indexMap.get(u))
					.forEach(i -> results[i] = new LaunchResult(node, t));
		}

		LaunchResult fullResult = new LaunchResult(null, new ResourceFullException(node));
		fr.leftovers.stream()
				.map(u -> fr.indexMap.get(u))
				.forEach(i -> results[i] = fullResult);

		for(Map.Entry<NodeInfo, Set<UUID>> succeeded : fr.toLaunch.entrySet()) {

		}

		return results;
	}

	private static Throwable configureNode(NodeInfo ni, LoginCredentials creds) {
		/* I feel gross returning exceptions. Also return null means success. */
		if(creds == null) {
			return new RuntimeException("No credentials for node");
		}

		if(!creds.getOptionalPassword().isPresent() && !creds.getOptionalPrivateKey().isPresent()) {
			return new IllegalStateException("no password or private key");
		}

		Optional<String> _privKey = Optional.ofNullable(creds.getOptionalPrivateKey().orNull());
		Optional<KeyPair> keyPair = Optional.empty();
		if(_privKey.isPresent()) {
			try {
				keyPair = Optional.of(ActuatorUtils.readPEMKey(_privKey.get()));
			} catch(IOException e) {
				return e;
			}
		}

		try {
			ni.configure(
					creds.getUser(),
					Optional.ofNullable(creds.getOptionalPassword().orNull()),
					keyPair
			);
		} catch(RuntimeException e) {
			return e;
		}
		return null;
	}

	@Override
	public boolean forceTerminateAgent(UUID uuid) {
		return false;
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

	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {

	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		return true;
	}

	@Override
	public boolean adopt(AgentState state) {
		return false;
	}

	private static Optional<Hardware> resolveHardware(ComputeService compute, String name) {
		return compute.listHardwareProfiles().stream()
				.filter(h -> name.equals(h.getName()))
				.findFirst()
				.map(h -> (Hardware)h);
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
