package au.edu.uq.rcc.nimrodg.resource.cloud;

import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import java.net.URI;
import java.security.KeyPair;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.jclouds.compute.domain.NodeMetadata;

class NodeInfo {

	public final NodeMetadata node;
	public final Set<UUID> agents;
	public final CompletableFuture<RemoteActuator> actuator;
	public final CompletableFuture<SSHResourceType.SSHConfig> sshConfig;

	boolean isConfigured;
	Optional<KeyPair> keyPair;
	List<URI> uris;

	public NodeInfo(NodeMetadata node) {
		this.node = node;
		this.agents = new HashSet<>();
		this.actuator = new CompletableFuture<>();
		this.sshConfig = new CompletableFuture<>();

		this.isConfigured = false;
		this.keyPair = Optional.empty();
		this.uris = List.of();
	}

	void configure(String username, Optional<String> password, Optional<KeyPair> keyPair) {
		if(isConfigured) {
			throw new IllegalStateException();
		}

		String userInfo = password.map(p -> String.format("%s:%s", username, p)).orElse(username);

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
