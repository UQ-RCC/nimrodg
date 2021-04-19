/*
 * Nimrod Portal Backend
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.impl.base.db.MigrationPlan;
import au.edu.uq.rcc.nimrodg.impl.postgres.NimrodAPIFactoryImpl;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;
import au.edu.uq.rcc.nimrodg.resource.hpc.HPCConfig;
import au.edu.uq.rcc.nimrodg.resource.hpc.HPCDefinition;
import au.edu.uq.rcc.nimrodg.resource.hpc.HPCResourceType;
import au.edu.uq.rcc.nimrodg.resource.ssh.ClientFactories;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hubspot.jinjava.Jinjava;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.json.JsonValue;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Controller
@ConfigurationProperties(prefix = "nimrod.remote")
@EnableConfigurationProperties
public class NimrodPortalEndpoints {
	private static final Logger LOGGER = LoggerFactory.getLogger(NimrodPortalEndpoints.class);

	private final Jinjava jinJava;
	private final String nimrodIniTemplate;
	private final Map<String, HPCDefinition> hpcDefs;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("pgbuilder")
	private UriBuilder postgresUriBuilder;

	@Autowired
	private RabbitManagementClient rabbit;

	@Autowired
	private ResourceClient resource;

	@Autowired
	private ObjectMapper objectMapper;

	private Map<String, String> remoteVars;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DefaultSetupConfig setupConfig;

	@Autowired
	private HttpServletRequest request;

	@Autowired
	@Value("${nimrod.max_job_count}")
	private long maxJobCount;

	public NimrodPortalEndpoints() {
		this.jinJava = new Jinjava();
		this.nimrodIniTemplate = NimrodUtils.readEmbeddedFileAsString(this.getClass(), "nimrod.ini.j2");

		try {
			hpcDefs = HPCResourceType.loadConfig(new Path[0], new ArrayList<>());
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@RequestMapping(method = {RequestMethod.PUT}, value = "/api/provision/{username}")
	@ResponseBody
	public ResponseEntity<Void> provisionUser(@PathVariable String username, JwtAuthenticationToken jwt) {
		UserState userState = getUserState(jwt, true);
		if(!userState.username.equals(username)) {
			LOGGER.warn("User {} attempted to provision {}", userState.username, username);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		HttpStatus code = rabbit.addUser(userState.amqpUser, userState.amqpPass).getStatusCode();
		if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
			LOGGER.error("Unable to add RabbitMQ user {}, status = {}", userState.username, code);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		code = rabbit.addVHost(userState.amqpUser).getStatusCode();
		if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
			LOGGER.error("Unable to add RabbitMQ vhost {}, status = {}", userState.username, code);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		code = rabbit.addPermissions(userState.amqpUser, userState.amqpUser, ".*", ".*", ".*").getStatusCode();
		if(!HttpStatus.CREATED.equals(code) && !HttpStatus.NO_CONTENT.equals(code)) {
			LOGGER.error("Unable to set RabbitMQ user {} permissions on vhost {}, status = {}", userState.username, userState.username, code);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		try(NimrodAPI nimrod = createNimrod(userState)) {
			/* nop */
		} catch(RuntimeException|SQLException e) {
			LOGGER.error(String.format("Unable to configure Nimrod for user %s", userState.username), e);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		/* Template the config file out to the HPC */
		ResponseEntity<String> jo;
		try {
			jo = resource.executeJob("setuserconfiguration", Map.of(
					"config", jinJava.render(nimrodIniTemplate, userState.vars)
			));
		} catch(HttpStatusCodeException e) {
			/* If we 401, pass that back to the user so they can refresh the token. */
			if(e.getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		if(!HttpStatus.OK.equals(jo.getStatusCode())) {
			LOGGER.error("Unable to write Nimrod configuration for user {}, status = {}", userState.username, jo.getStatusCode());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		return ResponseEntity.noContent().build();
	}


	@RequestMapping(method = {RequestMethod.GET}, value = "/api/experiments")
	@ResponseBody
	public ResponseEntity<JsonNode> getExperiments(JwtAuthenticationToken jwt) throws SQLException {
		UserState userState = getUserState(jwt);

		ArrayNode exps = objectMapper.createArrayNode();

		try(NimrodAPI nimrod = createNimrod(userState)) {
			for(Experiment exp : nimrod.getExperiments()) {
				exps.add(toJson(nimrod, exp));
			}
		}

		return ResponseEntity.ok(exps);
	}

	@RequestMapping(method = RequestMethod.POST, value = "/api/experiments")
	@ResponseBody
	public ResponseEntity<JsonNode> addExperiment(JwtAuthenticationToken jwt, UriComponentsBuilder uriComponentsBuilder, @RequestBody AddExperiment addExperiment) throws SQLException {
		UserState userState = getUserState(jwt);

		ArrayNode errors = objectMapper.createArrayNode();
		ObjectNode response = objectMapper.createObjectNode();
		response.set("result", objectMapper.nullNode());
		response.set("errors", errors);

		CompiledRun rf = compilePlanfile(addExperiment.planfile, errors);
		if(rf == null) {
			return ResponseEntity.badRequest().body(response);
		}

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(addExperiment.name);
			if(exp != null) {
				return ResponseEntity.status(HttpStatus.CONFLICT).build();
			}

			/* NB: Insert this before the actual experiment. */
			jdbc.update("INSERT INTO public.portal_planfiles(user_id, exp_name, planfile) VALUES(?, ?, ?) ON CONFLICT(user_id, exp_name) DO UPDATE SET planfile=EXCLUDED.planfile",
					userState.id,
					addExperiment.name,
					addExperiment.planfile
			);

			nimrod.addExperiment(addExperiment.name, rf);
		} catch(NimrodException.ExperimentExists e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}

		UriComponents uriComponents = uriComponentsBuilder.path("/api/experiments/{name}")
				.buildAndExpand(Map.of("name", addExperiment.name));

		return ResponseEntity.created(uriComponents.toUri()).build();
	}

	@RequestMapping(method = {RequestMethod.DELETE}, value = "/api/experiments/{expName}")
	@ResponseBody
	public ResponseEntity<JsonNode> deleteExperiment(JwtAuthenticationToken jwt, @PathVariable String expName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(expName);
			if(exp == null) {
				return ResponseEntity.notFound().build();
			}
			nimrod.deleteExperiment(exp);
		} catch(NimrodException.ExperimentActive e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}

		return ResponseEntity.noContent().build();
	}

	private CompiledRun compilePlanfile(String planfile, ArrayNode errors) {
		RunBuilder rb;
		try {
			rb = ANTLR4ParseAPIImpl.INSTANCE.parseRunToBuilder(planfile);
		} catch(PlanfileParseException ex) {
			for(PlanfileParseException.ParseError e : ex.getErrors()) {
				errors.add(objectMapper.createObjectNode()
						.put("line", e.line)
						.put("position", e.position)
						.put("message", e.message)
				);
			}
			return null;
		}

		if(rb.getEstimatedJobCount() >= maxJobCount) {
			errors.add(objectMapper.createObjectNode()
					.put("line", -1)
					.put("position", -1)
					.put("message", "Estimated job count too large.")
			);

			return null;
		}

		CompiledRun rf;
		try {
			rf = rb.build();
		} catch(RunfileBuildException e) {
			errors.add(objectMapper.createObjectNode()
					.put("line", -1)
					.put("position", -1)
					.put("message", e.getMessage())
			);
			return null;
		}

		return rf;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/api/compile")
	@ResponseBody
	public JsonNode compilePlanfile(HttpServletResponse httpResponse, @RequestParam(name = "planfile") String planfile) {
		httpResponse.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");

		ArrayNode errors = objectMapper.createArrayNode();
		ObjectNode response = objectMapper.createObjectNode();
		response.set("errors", errors);

		CompiledRun rf = compilePlanfile(planfile, errors);
		if(rf != null) {
			response.set("result", javaxJsonToJackson(JsonUtils.toJson(rf, false)));
		} else {
			response.set("result", objectMapper.nullNode());
		}

		return response;
	}

	private JsonNode javaxJsonToJackson(javax.json.JsonValue jv) {
		try {
			return objectMapper.readTree(jv.toString());
		} catch(JsonProcessingException e) {
			throw new IllegalStateException();
		}
	}

	@RequestMapping(method = {RequestMethod.GET}, value = "/api/experiments/{expName}")
	@ResponseBody
	public ResponseEntity<JsonNode> getExperiment(JwtAuthenticationToken jwt, @PathVariable String expName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(expName);
			if(exp == null) {
				return ResponseEntity.notFound().build();
			}

			ObjectNode jExp = toJson(nimrod, exp);

			SqlRowSet rs = jdbc.queryForRowSet("SELECT planfile FROM public.portal_planfiles WHERE user_id = ? AND exp_name = ?",
					userState.id,
					expName
			);

			if(rs.next()) {
				jExp.put("planfile", rs.getString("planfile"));
			} else {
				jExp.put("planfile", "");
			}

			return ResponseEntity.ok(jExp);
		}
	}

	@RequestMapping(method = {RequestMethod.POST}, value = "/api/experiments/{expName}/control")
	@ResponseBody
	public ResponseEntity<Void> controlExperiment(
			JwtAuthenticationToken jwt,
			@PathVariable String expName,
			@RequestParam(value = "action") String action,
			HttpServletRequest request)  throws SQLException {

		/* Mixing REST and RPC here :/ */
		if(action == null) {
			return ResponseEntity.badRequest().build();
		} else if(action.equalsIgnoreCase("start")) {
			return startExperiment(jwt, expName, request.getParameter("account"));
		} else {
			return ResponseEntity.badRequest().build();
		}
	}

	private ResponseEntity<Void> startExperiment(JwtAuthenticationToken jwt, String expName, String account) throws SQLException {
		if(expName == null || account == null) {
			return ResponseEntity.badRequest().build();
		}

		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(expName);
			if(exp == null) {
				return ResponseEntity.notFound().build();
			}

			/* If we're already started, do nothing. */
			if(exp.getState() != Experiment.State.STOPPED) {
				return ResponseEntity.noContent().build();
			}

			/*
			 * Bail if no resources are assigned.
			 * This is racy, but it's good enough for now.
			 */
			if(nimrod.getAssignedResources(exp).isEmpty()) {
				return ResponseEntity.unprocessableEntity().build();
			}
		}

		try {
			resource.executeJob("startexperiment", Map.of(
					"exp_name", expName,
					"account", account
			));
		} catch(HttpStatusCodeException e) {
			/* If we 401, pass that back to the user so they can refresh the token. */
			if(e.getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		return ResponseEntity.noContent().build();
	}

	@RequestMapping(method = {RequestMethod.GET}, value = "/api/experiments/{expName}/resources")
	@ResponseBody
	public ResponseEntity<JsonNode> getAssignments(JwtAuthenticationToken jwt, @PathVariable String expName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(expName);
			if(exp == null) {
				return ResponseEntity.notFound().build();
			}

			ArrayNode arr = objectMapper.createArrayNode();
			nimrod.getAssignedResources(exp).stream()
					.map(Resource::getName)
					.forEach(arr::add);
			return ResponseEntity.ok(arr);
		}
	}

	@RequestMapping(method = {RequestMethod.PUT}, value = "/api/experiments/{expName}/resources")
	@ResponseBody
	public ResponseEntity<JsonNode> setAssignments(JwtAuthenticationToken jwt, @PathVariable String expName, @RequestBody List<String> ds) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Experiment exp = nimrod.getExperiment(expName);
			if(exp == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
			}

			nimrod.getAssignedResources(exp)
					.forEach(r -> nimrod.unassignResource(r, exp));

			ds.stream()
					.map(nimrod::getResource)
					.filter(Objects::nonNull)
					.forEach(r -> nimrod.assignResource(r, exp));

			return ResponseEntity.noContent().build();
		}
	}

	@RequestMapping(method = RequestMethod.GET, value = "/api/resources")
	@ResponseBody
	public ResponseEntity<JsonNode> getResources(JwtAuthenticationToken jwt) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			return ResponseEntity.ok(toJson(nimrod.getResources()));
		}
	}

	@RequestMapping(method = RequestMethod.GET, value = "/api/resources/{resName}")
	@ResponseBody
	public ResponseEntity<JsonNode> getResource(JwtAuthenticationToken jwt, @PathVariable String resName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Resource res = nimrod.getResource(resName);
			if(res == null) {
				return ResponseEntity.notFound().build();
			}

			return ResponseEntity.ok(toJson(res));
		}
	}

	@RequestMapping(method = RequestMethod.POST, value = "/api/resources")
	@ResponseBody
	public ResponseEntity<Void> addResource(JwtAuthenticationToken jwt, UriComponentsBuilder uriComponentsBuilder, @RequestBody AddResource addResource) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			HPCConfig hpcc = new HPCConfig(
					new SSHResourceType.SSHConfig(
							nimrod.lookupAgentByPlatform("x86_64-pc-linux-musl"),
							ClientFactories.LOCAL_FACTORY,
							new TransportFactory.Config(
									Optional.empty(),
									new PublicKey[0],
									Optional.empty(),
									Optional.empty()
							),
							List.of()
					),
					addResource.limit,
					"TMPDIR",
					addResource.maxbatch,
					addResource.ncpu,
					addResource.mem * 1073741824L,
					addResource.hour * 3600,
					120,
					addResource.account,
					null,
					addResource.machine,
					hpcDefs.get("pbspro")
			);

			Resource res = nimrod.getResource(addResource.name);
			if(res != null) {
				return ResponseEntity.status(HttpStatus.CONFLICT).build();
			}

			nimrod.addResource(addResource.name, "hpc", hpcc.toJson(), null, null);
		} catch(NimrodException.ResourceExists e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}

		UriComponents uriComponents = uriComponentsBuilder.path("/api/resources/{name}")
				.buildAndExpand(Map.of("name", addResource.name));

		return ResponseEntity.created(uriComponents.toUri()).build();
	}

	@RequestMapping(method = RequestMethod.DELETE, value = "/api/resources/{resName}")
	@ResponseBody
	public ResponseEntity<JsonNode> deleteResource(JwtAuthenticationToken jwt, @PathVariable String resName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Resource res = nimrod.getResource(resName);
			if(res == null) {
				return ResponseEntity.notFound().build();
			}

			nimrod.deleteResource(res);
			return ResponseEntity.noContent().build();
		} catch(NimrodException.ResourceBusy e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		}
	}

	@RequestMapping(method = RequestMethod.GET, value = "/api/resources/{resName}/agents")
	@ResponseBody
	public ResponseEntity<JsonNode> getResourceAgents(JwtAuthenticationToken jwt, @PathVariable String resName) throws SQLException {
		UserState userState = getUserState(jwt);

		try(NimrodAPI nimrod = createNimrod(userState)) {
			Resource res = nimrod.getResource(resName);
			if(res == null) {
				return ResponseEntity.notFound().build();
			}

			Collection<? extends AgentInfo> agents = nimrod.getResourceAgents(res);

			ArrayNode jas = objectMapper.createArrayNode();
			agents.forEach(ai -> jas.add(toJson(ai)));

			return ResponseEntity.ok(jas);
		}
	}

	private ObjectNode toJson(AgentInfo ai) {
		ObjectNode on = objectMapper.createObjectNode()
				.put("state", ai.getState().toString())
				.put("queue", ai.getQueue())
				.put("uuid", ai.getUUID().toString())
				.put("shutdown_signal", ai.getShutdownSignal())
				.put("shutdown_reason", ai.getShutdownReason().toString().toLowerCase(Locale.ENGLISH)) /* FIXME */
				.put("creation_time", ai.getCreationTime().toString());

		Instant connTime = ai.getConnectionTime();
		if(connTime == null) {
			on.putNull("connection_time");
		} else {
			on.put("connection_time", ai.getConnectionTime().toString());
		}

		Instant lastTime = ai.getLastHeardFrom();
		if(lastTime == null) {
			on.putNull("last_heard_from");
		} else {
			on.put("last_heard_from", lastTime.toString());
		}

		Instant expireTime = ai.getExpiryTime();
		if(expireTime == null) {
			on.putNull("expiry_time");
		} else {
			on.put("expiry_time", expireTime.toString());
		}

		on.put("expired", ai.getExpired());

		/* on.put("secret_key", ai.getSecretKey()); */

		JsonValue actData = ai.getActuatorData();
		if(actData == null) {
			on.putNull("actuator_data");
		} else {
			on.set("actuator_data", javaxJsonToJackson(actData));
		}

		return on;
	}

	/* FIXME: These should be in Nimrod proper */
	private ObjectNode toJson(NimrodAPI nimrod, Experiment exp) {
		ArrayNode vars = objectMapper.createArrayNode();
		exp.getVariables().forEach(vars::add);

		ObjectNode on = objectMapper.createObjectNode()
				.put("name", exp.getName())
				.put("state", exp.getState().toString())
				.put("working_directory", exp.getWorkingDirectory())
				.put("creation_time", exp.getCreationTime().toString())
				.put("is_persistent", exp.isPersistent())
				.put("is_active", exp.isActive());

		on.set("variables", vars);
		on.set("tasks", javaxJsonToJackson(JsonUtils.toJson(exp.getTasks().values())));

		Collection<Job> jobs = nimrod.filterJobs(exp, EnumSet.allOf(JobAttempt.Status.class), 0, -1);
		int nComplete = 0, nFailed = 0, nPending = 0, nRunning = 0;
		for(Job j : jobs) {
			switch(j.getCachedStatus()) {
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
		return on;
	}

	private ArrayNode toJson(Collection<? extends Resource> resList) {
		ArrayNode ress = objectMapper.createArrayNode();
		for(Resource res : resList) {
			ress.add(toJson(res));
		}
		return ress;
	}

	private ObjectNode toJson(Resource res) {
		ObjectNode on = objectMapper.createObjectNode()
				.put("name", res.getName())
				.put("type", res.getTypeName());

		on.set("config", javaxJsonToJackson(res.getConfig()));
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
		return on;
	}

	private UserState getUserState(JwtAuthenticationToken jwt) throws ResponseStatusException {
		return getUserState(jwt, false);
	}

	private UserState getUserState(JwtAuthenticationToken jwt, boolean create) throws ResponseStatusException {
		if(jwt == null) {
			LOGGER.warn("getUserState() called with NULL token.");
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		String preferred_username = jwt.getToken().getClaimAsString("preferred_username");
		if(preferred_username == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}

		return getUserState(preferred_username, create);
	}

	private UserState getUserState(String username, boolean create) throws ResponseStatusException {
		SqlRowSet rs;
		if(!create) {
			rs = jdbc.queryForRowSet("SELECT id, pg_password, amqp_password, initialised FROM public.portal_user_status WHERE username = ?", username);
			if(!rs.next()) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN);
			}
		} else {
			rs = jdbc.queryForRowSet("SELECT id, pg_password, amqp_password, initialised FROM public.portal_create_user(?)", username);
			if(!rs.next()) {
				/* Hopefully, should never happen. */
				LOGGER.error("portal_create_user({}) returned no rows.", username);
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
			}
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

		vars.put("jdbc_url", jdbcUrl);

		UserState userState = new UserState(
				rs.getLong("id"),
				username,
				username,
				rs.getString("pg_password"),
				username,
				rs.getString("amqp_password"),
				rs.getBoolean("initialised"),
				jdbcUrl,
				vars
		);

		LOGGER.debug("{}: getUserState({}) => {id={}}", request.getRequestURI(), userState.username, userState.id);
		return userState;
	}

	private NimrodAPI createNimrod(UserState userState) throws SQLException {
		Objects.requireNonNull(userState, "userState");

		SetupConfig cfg = setupConfig.toBuilder(userState.vars).build();
		NimrodAPIFactoryImpl fact = new NimrodAPIFactoryImpl();

		LOGGER.trace("Creating NimrodAPI for user {}...", userState.username);

		/* NB: Can't use try-with-resources here, as we need the connection to live.  */
		Connection c = dataSource.getConnection();
		try {
			/* ...Also can't use prepareStatement() with SET. The username should always be safe regardless. */
			try(Statement stmt = c.createStatement()) {
				stmt.executeUpdate("SET search_path = " + userState.username);
			}

			/* Build an upgrade plan in case the user hasn't logged in in a bit. */
			MigrationPlan plan = fact.buildUpgradePlan(c);
			if(!plan.valid) {
				LOGGER.error("Built migration plan, details follow:");
				LOGGER.error(" Valid: {}", plan.valid);
				LOGGER.error("  From: {}", plan.from);
				LOGGER.error("    To: {}", plan.to);
				LOGGER.error(" Steps: {}", plan.path.size());
				/* This is a bug, should never happen. */
				throw new IllegalStateException("Invalid migration plan: " + plan.message);
			} else {
				LOGGER.trace("Built migration plan, details follow:");
				LOGGER.trace(" Valid: {}", plan.valid);
				LOGGER.trace("  From: {}", plan.from);
				LOGGER.trace("    To: {}", plan.to);
				LOGGER.trace(" Steps: {}", plan.path.size());
			}

			LOGGER.trace("Running migration...");
			try(Statement s = c.createStatement()) {
				s.executeUpdate(plan.sql);
			}

			/* NimrodAPI now owns the Connection. */
			NimrodAPI nimrod = fact.createNimrod(c);
			/* NB: This is idempotent. Will also correct anything that's been messed up. */
			LOGGER.trace("Configuring Nimrod...");
			NimrodUtils.setupApi(nimrod, cfg);

			LOGGER.trace("Fixing table permissions...");
			try(PreparedStatement s = c.prepareStatement("SELECT * FROM public.portal_fix_user(?)")) {
				s.setString(1, userState.username);
				try(ResultSet rs = s.executeQuery()) {
					if(!rs.next()) {
						/* Also a bug, should never happen. */
						throw new IllegalStateException("portal_fix_user(" + userState.username + ") failed, no rows returned");
					}
				}
			}
			return nimrod;
		} catch(RuntimeException|SQLException e) {
			try {
				c.close();
			} catch(SQLException ee) {
				ee.addSuppressed(e);
				throw ee;
			}
			throw e;
		}
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
	public static UriBuilder createPostgresUriBuilder(@Value("${nimrod.remote.postgres_uritemplate}") String s) {
		return new DefaultUriBuilderFactory().uriString(s);
	}

	@Bean
	@RequestScope
	public static ResourceClient createResourceClient(
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
	public static RabbitManagementClient createRabbitManagementClient(
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
