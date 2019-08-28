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

import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.act.RemoteActuator;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.jclouds.domain.LoginCredentials;

class NodeInfo {

	public final NodeMetadata node;
	public final Set<UUID> agents;

	CompletableFuture<RemoteActuator> actuator;
	CompletableFuture<SSHResourceType.SSHConfig> sshConfig;

	private boolean isConfigured;
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

	public static NodeInfo recover(NodeMetadata n, URI[] uris, SSHResourceType.SSHConfig sscfg) {
		NodeInfo ni = new NodeInfo(n);
		ni.sshConfig.complete(sscfg);
		ni.keyPair = sscfg.transportConfig.keyPair;
		ni.uris = List.of(uris);
		ni.isConfigured = true;
		return ni;
	}

	public static NodeInfo configure(NodeMetadata n) {
		NodeInfo ni = new NodeInfo(n);
		ni.configureFromNode();
		return ni;
	}

	public void configureFromNode() {
		if(isConfigured) {
			throw new IllegalStateException();
		}

		if(node.getPublicAddresses().isEmpty()) {
			throw new IllegalStateException("no public addresses");
		}

		LoginCredentials creds = node.getCredentials();
		if(creds == null) {
			throw new IllegalStateException("no login credentials for node");
		}

		if(!creds.getOptionalPassword().isPresent() && !creds.getOptionalPrivateKey().isPresent()) {
			throw new IllegalStateException("no password or private key");
		}

		Optional<String> _privKey = Optional.ofNullable(creds.getOptionalPrivateKey().orNull());
		Optional<KeyPair> _keyPair = Optional.empty();
		if(_privKey.isPresent()) {
			try {
				_keyPair = Optional.of(ActuatorUtils.readPEMKey(_privKey.get()));
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		String userInfo = Optional.ofNullable(creds.getOptionalPassword().orNull())
				.map(p -> String.format("%s:%s", creds.getUser(), p))
				.orElse(creds.getUser());

		this.keyPair = _keyPair;
		this.uris = node.getPublicAddresses().stream()
				.map(addr -> UriBuilder.fromUri("")
				.scheme("ssh")
				.host(addr)
				.userInfo(userInfo)
				.port(node.getLoginPort())
				.build()).collect(Collectors.toList());
		this.isConfigured = true;
	}
}
