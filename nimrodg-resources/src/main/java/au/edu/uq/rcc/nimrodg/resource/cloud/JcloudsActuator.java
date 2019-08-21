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
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceFullException;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
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
import org.jclouds.http.Uris;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.sshj.config.SshjSshClientModule;

public class JcloudsActuator implements Actuator {

	private static final Logger LOGGER = LogManager.getLogger(JcloudsActuator.class);

	private final Operations ops;
	private final NimrodAPI nimrod;
	private final Resource node;
	private final NimrodURI uri;
	private final Certificate[] certs;

	private final int agentsPerNode;
	private final CloudConfig config;
	private final Set<com.google.inject.Module> modules;
	private final ComputeService compute;
	private final Template template;
	private final String groupName;
	private final Map<NodeMetadata, NodeInfo> nodes;

	private static class NodeInfo {

		public final NodeMetadata node;
		public final Set<UUID> agents;

		private boolean isConfigured;
		private String username;
		private Optional<String> password;
		private Optional<KeyPair> keyPair;
		private List<URI> uris;

		public NodeInfo(NodeMetadata node) {
			this.node = node;
			this.agents = new HashSet<>();
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

	public JcloudsActuator(Operations ops, NimrodAPI nimrod, Resource node, NimrodURI uri, Certificate[] certs, int agentsPerNode, CloudConfig config) {
		this.ops = ops;
		this.nimrod = nimrod;
		this.node = node;
		this.uri = uri;
		this.certs = certs;
		this.agentsPerNode = agentsPerNode;
		this.config = config;
		this.modules = Set.of(new SshjSshClientModule()); // TODO: Wrap our existing ssh functionality
		this.compute = createComputeService();

//		TemplateOptions opts = NovaTemplateOptions.Builder.inboundPorts(22)
//				.availabilityZone("QRIScloud")
//				.generateKeyPair(true)
//				.blockOnComplete(false)
//				.blockUntilRunning(false)
//				.networks("283e92a3-40dc-482f-bb94-9f4632c0190b");
		TemplateOptions opts = NovaTemplateOptions.Builder
				.availabilityZone("QRIScloud")
				.generateKeyPair(true)
				.inboundPorts(22)
				.userMetadata(Map.of(
						"availability_zone", config.availabilityZone,
						"OS-EXT-AZ:availability_zone", config.availabilityZone
				))
				//.generateKeyPair(true)
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
	}

	private ComputeService createComputeService() {
		return ContextBuilder.newBuilder(config.contextName)
				.endpoint(config.endpoint.toString())
				.credentials(config.username, config.password)
				.overrides(config.props)
				.modules(Set.of(new SshjSshClientModule()))
				.buildView(ComputeServiceContext.class)
				.getComputeService();
	}

	@Override
	public Resource getResource() throws NimrodAPIException {
		return node;
	}

	@Override
	public NimrodURI getAMQPUri() {
		return uri;
	}

	@Override
	public Certificate[] getAMQPCertificates() {
		return Arrays.copyOf(certs, certs.length);
	}

	private void destroyBadNodes(RunNodesException e) {
		for(Map.Entry<? extends NodeMetadata, ? extends Throwable> nodeError : e.getNodeErrors().entrySet()) {
			compute.destroyNode(nodeError.getKey().getId());
		}
	}

	private void launchNodes(int num, Set<NodeMetadata> good, Map<NodeMetadata, Throwable> bad) {
		try {
			good.addAll(compute.createNodesInGroup(groupName, num, template));
		} catch(RunNodesException e) {
			good.addAll(e.getSuccessfulNodes());

			//e.g
			// TODO: This
		}
	}

	@Override
	public LaunchResult[] launchAgents(UUID[] uuids) throws IOException {
		LaunchResult[] results = new LaunchResult[uuids.length];
		LaunchResult failedResult = new LaunchResult(null, new ResourceFullException(node));

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
		Map<NodeMetadata, Throwable> bad = new HashMap<>();
		Set<NodeMetadata> _good = new HashSet<>();
		if(nLaunchable < uuids.length) {
			launchNodes(numRequired, _good, bad);
		}

		/*
		 * Add all spawned nodes now, so we still know about them if something throws.
		 * I'm not confident enough in the validation code below to not do this.
		 */
		_good.forEach(n -> nodes.put(n, new NodeInfo(n)));

		/* Validate the credentials for each node. */
		for(NodeMetadata n : _good) {
			final LoginCredentials creds = n.getCredentials();
			if(creds == null) {
				bad.put(n, new RuntimeException("No credentials for node"));
				continue;
			}

			if(!creds.getOptionalPassword().isPresent() && !creds.getOptionalPrivateKey().isPresent()) {
				bad.put(n, new IllegalStateException("no password or private key"));
				continue;
			}

			Optional<String> _privKey = Optional.ofNullable(creds.getOptionalPrivateKey().orNull());
			Optional<KeyPair> keyPair = Optional.empty();
			if(_privKey.isPresent()) {
				try {
					keyPair = Optional.of(ActuatorUtils.readPEMKey(_privKey.get()));
				} catch(IOException e) {
					bad.put(n, e);
					continue;
				}
			}

			NodeInfo ni = nodes.get(n);
			ni.configure(
					creds.getUser(),
					Optional.ofNullable(creds.getOptionalPassword().orNull()),
					keyPair
			);
			good.add(ni);
		}

		/* FIXME: Untested */
		bad.keySet().forEach(n -> compute.destroyNode(n.getId()));

		/*
		 * NB: `good` contains a list of nodes, sorted by the number of agents in descending order.
		 * Any new nodes are stored in the global list now, so they can be accounted for later incase
		 * the actual agent launches fails for some reason.
		 */
//
//		{
//			class Tmp {
//
//				public final NodeInfo ni;
//				public int nleft;
//
//				public Tmp(NodeInfo ni, int nleft) {
//					this.ni = ni;
//					this.nleft = nleft;
//				}
//
//			}
//
//			/* Assign agents to nodes. */
//			good.stream()
//					.map(n -> NimrodUtils.getOrAddLazy(nodes, n, nn -> new NodeInfo(nn)));
//			Queue<NodeInfo> nqueue = good.stream()
//					.map(n -> NimrodUtils.getOrAddLazy(nodes, n, nn -> new NodeInfo(nn)))
//					.collect(Collectors.toCollection(() -> new ArrayDeque<>()));
//
//			NodeInfo[] agents = new NodeInfo[uuids.length];
//			Map<NodeMetadata, Integer> frees = new HashMap<>();
//			good.forEach(n -> frees.put(n, 0));
//
//			int i;
//			for(i = 0; i < uuids.length; ++i) {
//				NodeInfo ni = nqueue.peek();
//				if(ni == null) {
//					break;
//				}
//
//				ni.agents.add(uuids[i]);
//				if(ni.agents.size() >= agentsPerNode) {
//					nqueue.poll();
//				}
//
//				agents[i] = ni;
//
//			}
//		}
////		for(NodeMetadata n : good) {
////			NodeInfo ni = NimrodUtils.getOrAddLazy(nodes, n, nn -> new NodeInfo(nn));
////
////		}
		return results;
	}

	@Override
	public boolean forceTerminateAgent(UUID uuid) {
		return false;
	}

//	private RemoteShell makeSsh(NodeInfo n) throws IOException {
//		return new SSHClient(
//				n.uri,
//				new PublicKey[0], n.keyPair.get());
//	}
	@Override
	public void close() throws IOException {
		for(NodeInfo ni : nodes.values()) {
			/* Ignore nodes that failed to set up and are still here for some reason. */
			if(!ni.isConfigured) {
				continue;
			}

			if(ni.agents.isEmpty()) {
				continue;
			}

//			try(RemoteShell rsh = makeSsh(ni)) {
//				// TODO: Get agent PIDs 
//				//RemoteActuator.kill(rsh, ni.agents.stream().mapToInt();
//			}
		}

		nodes.values().stream()
				.flatMap(ni -> ni.agents.stream())
				.forEach(u -> {

				});
		compute.destroyNodesMatching(n -> groupName.equals(n.getGroup()));
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
}
