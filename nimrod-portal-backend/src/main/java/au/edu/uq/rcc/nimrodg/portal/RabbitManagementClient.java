/*
 * Nimrod Portal Backend
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
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
package au.edu.uq.rcc.nimrodg.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@SuppressWarnings("WeakerAccess")
public class RabbitManagementClient {

	private final RestOperations rabbitRest;
	private final URI managementApi;
	private final String managementUser;
	private final String managementPassword;
	private final ObjectMapper objectMapper;

	public RabbitManagementClient(RestOperations restOps, URI api, String managementUser, String managementPassword) {
		this.rabbitRest = restOps;
		this.managementApi = api;
		this.managementUser = managementUser;
		this.managementPassword = managementPassword;
		this.objectMapper = new ObjectMapper();
	}

	public ResponseEntity<Void> addUser(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(managementUser, managementPassword, StandardCharsets.UTF_8);

		String payload = objectMapper.createObjectNode()
				.put("password", password)
				.put("tags", "")
				.toString();

		URI uri = UriComponentsBuilder.fromUri(managementApi)
				.pathSegment("api", "users", username)
				.build(new Object[0]);

		try {
			return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(payload, headers), Void.class);
		} catch(HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).build();
		}
	}

	public ResponseEntity<Void> addVHost(String vhost) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(managementUser, managementPassword, StandardCharsets.UTF_8);

		URI uri = UriComponentsBuilder.fromUri(managementApi)
				.pathSegment("api", "vhosts", vhost)
				.build(new Object[0]);

		try {
			return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
		} catch(HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).build();
		}
	}

	public ResponseEntity<Void> addPermissions(String vhost, String username, String configure, String read, String write) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.setBasicAuth(managementUser, managementPassword, StandardCharsets.UTF_8);

		String payload = objectMapper.createObjectNode()
				.put("configure", configure)
				.put("read", read)
				.put("write", write)
				.toString();

		URI uri = UriComponentsBuilder.fromUri(managementApi)
				.pathSegment("api", "permissions", vhost, username)
				.build(new Object[0]);

		try {
			return rabbitRest.exchange(uri, HttpMethod.PUT, new HttpEntity<>(payload, headers), Void.class);
		} catch(HttpStatusCodeException e) {
			return ResponseEntity.status(e.getStatusCode()).build();
		}
	}
}
