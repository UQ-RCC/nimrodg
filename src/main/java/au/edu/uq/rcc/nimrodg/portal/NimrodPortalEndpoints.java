/*
 * Nimrod Portal Backend
 * https://github.com/UQ-RCC/nimrod-portal-backend
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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.impl.postgres.NimrodAPIFactoryImpl;
import au.edu.uq.rcc.nimrodg.setup.NimrodSetupAPI;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hubspot.jinjava.Jinjava;
import org.apache.coyote.Response;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@ConfigurationProperties(prefix = "nimrod.remote")
@EnableConfigurationProperties
public class NimrodPortalEndpoints {
	private static final Logger LOGGER = LoggerFactory.getLogger(NimrodPortalEndpoints.class);

	private final Jinjava jinJava;
	private final String nimrodIniTemplate;
	private final String setupIniTemplate;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("pgbuilder")
	private UriBuilder postgresUriBuilder;

	@Autowired
	@Qualifier("rabbitbuilder")
	private UriBuilder rabbitUriBuilder;

	@Autowired
	private RabbitManagementClient rabbit;

	@Autowired
	private ResourceClient resource;

	@Autowired
	private ObjectMapper objectMapper;

	private Map<String, String> remoteVars;

//	@Autowired
//	@Value("${nimrod.remote.setup}")
//	private String nimrodSetup;

//	@Autowired
//	private SetupConfigBuilder nimrodConfig;


	public static class UserState {
		public final String username;
		public final String dbUser;
		public final String dbPass;

		public final String amqpUser;
		public final String amqpPass;
		public final boolean initialised;

		public final String jdbcUrl;
		public final String amqpUrl;

		public final Map<String, String> vars;

		public UserState(String username, String dbUser, String dbPass, String amqpUser, String amqpPass, boolean initialised, String jdbcUrl, String amqpUrl, Map<String, String> vars) {
			this.username = username;
			this.dbUser = dbUser;
			this.dbPass = dbPass;
			this.amqpUser = amqpUser;
			this.amqpPass = amqpPass;
			this.initialised = initialised;
			this.jdbcUrl = jdbcUrl;
			this.amqpUrl = amqpUrl;
			this.vars = Map.copyOf(vars);
		}
	}

	public NimrodPortalEndpoints() {
		this.jinJava = new Jinjava();

		try(InputStream is = NimrodPortalEndpoints.class.getResourceAsStream("nimrod.ini.j2")) {
			nimrodIniTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}

		try(InputStream is = NimrodPortalEndpoints.class.getResourceAsStream("nimrod-setup.ini.j2")) {
			setupIniTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@RequestMapping(method = {RequestMethod.PUT}, value = "/api/provision/{username}")
	@ResponseBody
	public ResponseEntity<Void> provisionUser(@PathVariable String username, JwtAuthenticationToken jwt) {
		UserState userState = getUserState(jwt);
		if(!userState.username.equals(username)) {
			LOGGER.warn("User {} attempted to provision {}", userState.username, username);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		try {
			HttpStatus code = rabbit.addUser(userState.amqpUser, userState.amqpPass).getStatusCode();
			if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
			}

			code = rabbit.addVHost(userState.amqpUser).getStatusCode();
			if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
			}

			code = rabbit.addPermissions(userState.amqpUser, userState.amqpUser, ".*", ".*", ".*").getStatusCode();
			if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
			}
		} catch(HttpStatusCodeException e) {
			//TODO: Log
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		// TODO: Change this to use NimrodSetupAPI
		ResponseEntity<String> jo;
		try {
			jo = resource.executeJob("setuserconfiguration", Map.of(
					"config", jinJava.render(nimrodIniTemplate, userState.vars),
					"setup_config", jinJava.render(setupIniTemplate, userState.vars),
					"initialise", userState.initialised ? "0" : "1"
			));
		} catch(HttpStatusCodeException e) {
			/* If we 401, pass that back to the user so they can refresh the token. */
			if(e.getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		if(!HttpStatus.OK.equals(jo.getStatusCode())) {
			// TODO: log
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}


	@RequestMapping(method = {RequestMethod.GET}, value = "/api/experiments")
	@ResponseBody
	public ResponseEntity<JsonNode> getExperiments(JwtAuthenticationToken jwt) {
		UserState userState = getUserState(jwt);

		ArrayNode exps = objectMapper.createArrayNode();

		try(NimrodAPI nimrod = createNimrod(userState.jdbcUrl, userState.dbUser, userState.dbPass)) {
			for(Experiment exp : nimrod.getExperiments()) {
				ArrayNode vars = objectMapper.createArrayNode();
				exp.getVariables().forEach(vars::add);

				ObjectNode on = objectMapper.createObjectNode()
						.put("name", exp.getName())
						.put("state", exp.getState().toString())
						.put("working_directory", exp.getWorkingDirectory())
						.put("creation_time", exp.getCreationTime().toString())
						.put("token", exp.getToken())
						.put("is_persistent", exp.isPersistent())
						.put("is_active", exp.isActive());

				on.set("variables", vars);
				on.set("tasks", objectMapper.readTree(JsonUtils.toJson(exp.getTasks().values()).toString()));

				Collection<Job> jobs = exp.filterJobs(EnumSet.allOf(JobAttempt.Status.class), 0, -1);
				int nComplete = 0, nFailed = 0, nPending = 0, nRunning = 0;
				for(Job j : jobs) {
					switch(j.getStatus()) {
						case COMPLETED:
							++nComplete;
							break;
						case RUNNING:
							++nRunning;
							break;
						case FAILED:
							++nFailed;
							break;
						case NOT_RUN:
							++nPending;
							break;
					}
				}
				on.put("total_jobs", jobs.size());
				on.put("completed_jobs", nComplete);
				on.put("running_jobs", nRunning);
				on.put("failed_jobs", nFailed);
				on.put("pending_jobs", nPending);

				exps.add(on);
			}
		} catch(Exception e) {
			//TODO: Log
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		return ResponseEntity.status(HttpStatus.OK).body(exps);
	}


	@RequestMapping(method = {RequestMethod.GET}, value = "/api/resources")
	@ResponseBody
	public ResponseEntity<JsonNode> getResources(JwtAuthenticationToken jwt) {
		UserState userState = getUserState(jwt);

		ArrayNode ress = objectMapper.createArrayNode();
		try(NimrodAPI nimrod = createNimrod(userState.jdbcUrl, userState.dbUser, userState.dbPass)) {
			for(Resource res : nimrod.getResources()) {
				ObjectNode on = objectMapper.createObjectNode()
						.put("name", res.getName())
						.put("type", res.getTypeName());

				on.set("config", objectMapper.readTree(res.getConfig().toString()));
				on.putObject("amqp")
						.put("uri", res.getAMQPUri().uri.toString())
						.put("cert", res.getAMQPUri().certPath)
						.put("no_verify_peer", res.getAMQPUri().noVerifyPeer)
						.put("no_verify_host", res.getAMQPUri().noVerifyHost);
				on.putObject("tx")
						.put("uri", res.getTransferUri().uri.toString())
						.put("cert", res.getTransferUri().certPath)
						.put("no_verify_peer", res.getTransferUri().noVerifyPeer)
						.put("no_verify_host", res.getTransferUri().noVerifyHost);
				ress.add(on);
			}

		} catch(Exception e) {
			//TODO: Log
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		return ResponseEntity.status(HttpStatus.OK).body(ress);
	}

	private UserConfig buildUserConfig(String uri, String dbUser, String dbPass) {
		return new UserConfig() {
			@Override
			public String factory() {
				return NimrodAPIFactoryImpl.class.getCanonicalName();
			}

			@Override
			public Map<String, Map<String, String>> config() {
				return Map.of("postgres", Map.of(
						"url", uri,
						"username", dbUser,
						"password", dbPass,
						"driver", "org.postgresql.Driver"
				));
			}
		};
	}


	private UserState getUserState(JwtAuthenticationToken jwt) throws ResponseStatusException {
		if(jwt == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		String preferred_username = jwt.getToken().getClaimAsString("preferred_username");
		if(preferred_username == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		return getUserState(preferred_username);

	}

	private UserState getUserState(String username) throws ResponseStatusException {
		SqlRowSet rs = jdbc.queryForRowSet("SELECT pg_password, amqp_password, initialised FROM portal_create_user(?)", username);
		if(!rs.next()) {
			/* Hopefully, should never happen. */
			LOGGER.error("portal_create_user({}) returned no rows.", username);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
		}

		String pgPass = rs.getString("pg_password");
		String amqpPass = rs.getString("amqp_password");

		Map<String, String> vars = new HashMap<>(remoteVars);
		vars.put("username", username);
		vars.put("pg_username", username);
		vars.put("pg_password", pgPass);
		vars.put("amqp_username", username);
		vars.put("amqp_password", amqpPass);
		vars.put("amqp_routing_key", username);

		String jdbcUrl = String.format("jdbc:%s", postgresUriBuilder.build(vars));
		String amqpUrl = rabbitUriBuilder.build(vars).toString();

		vars.put("jdbc_url", jdbcUrl);
		vars.put("amqp_url", amqpUrl);

		return new UserState(
				username,
				username,
				rs.getString("pg_password"),
				username,
				rs.getString("amqp_password"),
				rs.getBoolean("initialised"),
				jdbcUrl,
				amqpUrl,
				vars
		);
	}


	private NimrodAPI createNimrod(String uri, String dbUser, String dbPass) throws Exception {
		/* TODO: Make this use Spring's Hikari pool somehow. */
		return new NimrodAPIFactoryImpl().createNimrod(buildUserConfig(uri, dbUser, dbPass));
	}

	@SuppressWarnings("WeakerAccess")
	public static Certificate[] readX509Certificates(Path path) throws IOException, CertificateException {
		try(InputStream is = Files.newInputStream(path)) {
			return readX509Certificates(is);
		}
	}

	@SuppressWarnings("WeakerAccess")
	public static Certificate[] readX509Certificates(InputStream is) throws CertificateException {
		return CertificateFactory.getInstance("X.509").generateCertificates(is).stream().toArray(Certificate[]::new);
	}

	private static SSLContext buildSSLContext(Path cacert) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, KeyManagementException {
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);

		Certificate[] certs = readX509Certificates(cacert);
		for(int i = 0; i < certs.length; ++i) {
			String name = String.format("cert%d", i);
			ks.setCertificateEntry(name, certs[i]);
		}

		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, null);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);

		return new SSLContextBuilder()
				.loadTrustMaterial(ks, null)
				.build();
	}

	@Bean
	@Qualifier("pgbuilder")
	public UriBuilder createPostgresUriBuilder(@Value("${nimrod.remote.postgres_uritemplate}") String s) {
		return new DefaultUriBuilderFactory().uriString(s);
	}

	@Bean
	@Qualifier("rabbitbuilder")
	public UriBuilder createRabbitUriBuilder(@Value("${nimrod.remote.rabbit_uritemplate}") String s) {
		return new DefaultUriBuilderFactory().uriString(s);
	}

	@Bean
	@RequestScope
	public ResourceClient createResourceClient(
			@Value("${nimrod.resource.api}") URI api,
			@Value("${nimrod.resource.cacert:#{null}}") String cacert
	) throws GeneralSecurityException, IOException {
		JwtAuthenticationToken jwt = (JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication();
		String accessToken = jwt.getToken().getTokenValue();

		HttpClientBuilder b = HttpClients.custom()
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);

		if(cacert != null) {
			b.setSSLContext(buildSSLContext(Paths.get(cacert)));
		}

		b.addInterceptorFirst((HttpRequestInterceptor)(request, context) ->
				request.addHeader("Authorization", String.format("Bearer %s", accessToken))
		);

		RestTemplate rest = new RestTemplate(new HttpComponentsClientHttpRequestFactory(b.build()));

		return new ResourceClient(rest, api);
	}

	@Bean
	public RabbitManagementClient createRabbitManagementClient(
			@Value("${nimrod.rabbitmq.api}") URI api,
			@Value("${nimrod.rabbitmq.user}") String user,
			@Value("${nimrod.rabbitmq.password}") String password,
			@Value("${nimrod.rabbitmq.cacert:#{null}}") String cacert
	) throws GeneralSecurityException, IOException {
		HttpClientBuilder b = HttpClients.custom()
				.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);

		if(cacert != null) {
			b.setSSLContext(buildSSLContext(Paths.get(cacert)));
		}

		RestTemplate rest = new RestTemplate(new HttpComponentsClientHttpRequestFactory(b.build()));

		return new RabbitManagementClient(rest, api, user, password);
	}

	/* Needed so Spring can set it. */
	@SuppressWarnings("unused")
	public void setVars(Map<String, String> vars) {
		this.remoteVars = vars;
	}
}
