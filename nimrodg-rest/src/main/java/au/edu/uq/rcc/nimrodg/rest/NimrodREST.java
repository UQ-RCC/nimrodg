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
package au.edu.uq.rcc.nimrodg.rest;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Task;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilderException;

@Path("")
@Produces({MediaType.APPLICATION_JSON})
public class NimrodREST {

	public static final String REGEX_PATH = "[a-zA-Z0-9_]+(?:(?:\\/[a-zA-Z0-9_]+)+)?";
	public static final String REGEX_IDENTIFIER = "[a-zA-Z_][a-zA-Z0-9_]+";

	public static final String PP_PATH = "{path: " + REGEX_PATH + "}";
	public static final String PP_EXP = "{exp: " + REGEX_IDENTIFIER + "}";
	public static final String PP_EXP_JOB = PP_EXP + "/{job_index: [0-9]+}";

	@NimrodAPIParam
	private NimrodAPI nimrod;

	@Context
	private HttpServletRequest request;

	public static JsonObject toJson(AgentInfo ai) {
		JsonArrayBuilder ja = Json.createArrayBuilder();

		ai.posixMappings().stream().map(e -> Json.createObjectBuilder()
				.add("system", e.getKey())
				.add("machine", e.getValue())
				.build()).forEach(e -> ja.add(e));

		return Json.createObjectBuilder()
				.add("platform_string", ai.getPlatformString())
				.add("path", ai.getPath())
				.add("posix_mappings", ja).build();
	}

	public static JsonObject toJson(NimrodURI nuri) {
		return Json.createObjectBuilder()
				.add("uri", nuri.uri.toString())
				.add("cert_path", nuri.certPath)
				.add("no_verify_peer", nuri.noVerifyPeer)
				.add("no_verify_host", nuri.noVerifyHost)
				.build();
	}

	public static JsonObject toJson(NimrodConfig cfg) {
		return Json.createObjectBuilder()
				.add("work_dir", cfg.getWorkDir())
				.add("store_dir", cfg.getRootStore())
				.add("amqp", toJson(cfg.getAmqpUri()))
				.add("amqp_routing_key", cfg.getAmqpRoutingKey())
				.add("tx", toJson(cfg.getTransferUri()))
				.build();
	}

	public static JsonObject toJson(Map<String, String> sm) {
		JsonObjectBuilder jo = Json.createObjectBuilder();
		sm.entrySet().forEach(e -> jo.add(e.getKey(), e.getValue()));
		return jo.build();
	}

	public static JsonObject toJson(String k, String v) {
		JsonObjectBuilder job = Json.createObjectBuilder()
				.add("key", k);

		if(v == null) {
			job.add("value", JsonValue.NULL);
		} else {
			job.add("value", v);
		}
		return job.build();
	}

	public static JsonObject toJson(Command command) {
		return Json.createObjectBuilder().build();
	}

	public static JsonObject toJson(Task task) {
		JsonArrayBuilder jab = Json.createArrayBuilder();
		task.getCommands().forEach(c -> jab.add(toJson(c)));
		return Json.createObjectBuilder()
				.add("name", Task.taskNameToString(task.getName()))
				.add("commands", jab)
				.build();
	}

	public static JsonObject toJson(Experiment exp) {
		JsonArrayBuilder va = Json.createArrayBuilder();
		exp.getVariables().forEach(va::add);

		JsonObjectBuilder tb = Json.createObjectBuilder();
		exp.getTasks().forEach(t -> tb.add(Task.taskNameToString(t.getName()), toJson(t)));

		return Json.createObjectBuilder()
				.add("name", exp.getName())
				.add("state", Experiment.stateToString(exp.getState()))
				.add("working_directory", exp.getWorkingDirectory())
				.add("creation_time", exp.getCreationTime().toString())
				.add("variables", va)
				.add("tasks", tb)
				.add("token", exp.getToken())
				.add("is_persistent", exp.isPersistent())
				.add("is_active", exp.isActive())
				.build();
	}

	public static NimrodURI jsonToNimrodUri(JsonObject jo) {
		return NimrodURI.create(
				URI.create(jo.getString("uri")),
				jo.getString("cert_path"),
				jo.getBoolean("no_verify_peer"),
				jo.getBoolean("no_verify_host")
		);
	}

	@GET
	@Path("/agent")
	@Produces("application/json")
	public Response lookupAgents() {
		JsonObjectBuilder job = Json.createObjectBuilder();
		nimrod.lookupAgents().forEach((p, ai) -> job.add(p, toJson(ai)));

		return Response.ok()
				.entity(job.build())
				.build();
	}

	@GET
	@Path("/agent/platform/{platform}")
	@Produces("application/json")
	public Response lookupAgentByPlatform(@PathParam("platform") String platform) {
		AgentInfo ai = nimrod.lookupAgentByPlatform(platform);
		if(ai == null) {
			return Response.status(Response.Status.NOT_FOUND).entity("").build();
		}

		return Response.ok()
				.entity(toJson(ai))
				.build();
	}

	@GET
	@Path("/agent/posix/{system}/{machine}")
	@Produces("application/json")
	public Response lookupAgentByPosix(@PathParam("system") String system, @PathParam("machine") String machine) {
		AgentInfo ai = nimrod.lookupAgentByPosix(system, machine);
		if(ai == null) {
			return Response.status(Response.Status.NOT_FOUND).entity("").build();
		}

		return Response.ok()
				.entity(toJson(ai))
				.build();
	}

	@GET
	@Path("/config")
	@Produces("application/json")
	public Response getConfig() {
		return Response.ok()
				.entity(toJson(nimrod.getConfig()))
				.build();
	}

	@PUT
	@Path("/config")
	@Produces("application/json")
	@Consumes("application/json")
	public Response updateConfig(JsonStructure j) {
		JsonObject jo = (JsonObject)j;
		nimrod.updateConfig(
				jo.getString("work_dir"),
				jo.getString("store_dir"),
				jsonToNimrodUri(jo.getJsonObject("amqp")),
				jo.getString("amqp_routing_key"),
				jsonToNimrodUri(jo.getJsonObject("tx"))
		);
		return this.getConfig();
	}

	@GET
	@Path("/properties")
	@Produces("application/json")
	public Response getProperties() {
		return Response.ok().entity(toJson(nimrod.getProperties())).build();
	}

	@GET
	@Path("properties/{property}")
	@Produces("application/json")
	public Response getProperty(@PathParam("property") String prop) {
		return nimrod.getProperty(prop)
				.map(v -> Response.ok().entity(toJson(prop, v)))
				.orElse(Response.status(Response.Status.NOT_FOUND).entity(""))
				.build();
	}

	@PUT
	@Path("properties/{property}")
	public Response setProperty(@PathParam("property") String prop, JsonObject val) {
		/* Be pedantic. */
		if(val.containsKey("key") && !prop.equals(val.getString("key"))) {
			return Response.status(Response.Status.BAD_REQUEST).entity("").build();
		}

		/* Can't delete something via PUTting */
		String _val = val.getString("value");
		if(_val == null || _val.isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("").build();
		}

		Optional<String> oldVal = nimrod.setProperty(prop, _val);

		Response.Status status;
		if(!oldVal.isPresent()) {
			status = Response.Status.CREATED;
		} else {
			status = Response.Status.OK;
		}

		return Response
				.status(status)
				.entity(toJson(prop, oldVal.orElse(null)))
				.build();
	}

	@DELETE
	@Path("properties/{property}")
	public Response deleteProperty(@PathParam("property") String prop) {
		return nimrod.setProperty(prop, null)
				.map(v -> Response.ok().entity(toJson(prop, v)))
				.orElse(Response.status(Response.Status.NO_CONTENT).entity(""))
				.build();
	}

	@GET
	@Path("experiments/")
	public Response getExperiments() {
		JsonObjectBuilder job = Json.createObjectBuilder();
		nimrod.getExperiments().forEach(e -> job.add(e.getName(), toJson(e)));
		return Response.ok().entity(job.build()).build();
	}

//	@GET
//	@Path(PP_EXP)
//	public String getExperiment(@PathParam("exp") String experiment) {
//		return String.format("experiment %s", experiment);
//	}
//
//	@GET
//	@Path(PP_EXP_RUN)
//	public String getRun(
//			@PathParam("exp") String experiment,
//			@PathParam("run") String run) {
//		return String.format("experiment %s, run %s", experiment, run);
//	}
//
//	@GET
//	@Path(PP_EXP_RUN_JOB)
//	public String getJob(
//			@PathParam("exp") String experiment,
//			@PathParam("run") String run,
//			@PathParam("job_index") long jobIndex) {
//		return String.format("experiment %s, run %s, job: %s", experiment, run, jobIndex);
//	}
//
//	@PROPFIND
//	@Path(PP_EXP_RUN + "/storage{path:/?.*}")
//	public Response copyStorage(
//			@PathParam("exp") String expName,
//			@PathParam("run") String runName,
//			@PathParam("path") String path) {
//
//		request.getServletContext().getReq
//		return Response.status(Response.Status.FORBIDDEN).build();
//	}
//	@Path(PP_EXP_RUN + "/storage{path:/?.*}")
//	public Object handleStorage() {
//		return new Object() {
//			@GET
//			public void get(
//					@Context HttpServletRequest request,
//					@Context HttpServletResponse response,
//					@PathParam("exp") String expName,
//					@PathParam("run") String runName,
//					@PathParam("path") String path) throws ServletException, IOException {
//				ServletContext cctx = request.getServletContext().getContext("/storage/");
//				RequestDispatcher rq = cctx.getRequestDispatcher("/storage/");
//				rq.forward(request, response);
//			}
//		};
//	}
	//@GET
	//@Path(PP_EXP_RUN + "/storage{path:/?.*}")
	public void getStorage(
			@Context HttpServletResponse response,
			@PathParam("exp") String expName,
			@PathParam("path") String path) throws IOException, ServletException {

		/* Handle the case where they requested "/storage" */
		if(!path.startsWith("/")) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

//		String authToken = request.getHeader("X-NimrodG-File-Auth-Token");
//
//		if(authToken == null) {
//			response.sendError(HttpServletResponse.SC_FORBIDDEN);
//			return;
//		}
		Experiment exp = nimrod.getExperiment(expName);
		if(exp == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

//		NimrodEntity ent = api.isTokenValidForStorage(run, authToken);
//		if(ent == null) {
//			response.sendError(HttpServletResponse.SC_FORBIDDEN);
//			return;
//		}
//
//		if(ent.equals(run)) {
//			// Is a run token
//		} else {
//			// Is a attempt token
//		}
		java.nio.file.Path expDir = Paths.get(nimrod.getConfig().getRootStore()).resolve(exp.getWorkingDirectory());

		try {
			/*
			 * Validate the path. Convert to a URI and normalise it, but this
			 * will only go so far.
			 */
			URI uri = new URI("file", "", path, null).normalize();

			/* Even though it's been resolved, there may be leading relative components. */
			path = uri.getPath();
			if(path.startsWith("/..")) {
				throw new IllegalArgumentException();
			}
			/* Strip the leading '/' */
			path = path.substring(1);
		} catch(IllegalArgumentException | UriBuilderException | URISyntaxException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		java.nio.file.Path ppp = expDir.resolve(path);

		ServletContext cctx = request.getServletContext().getContext("/storage/");
		RequestDispatcher rq = cctx.getRequestDispatcher("/storage/");
		rq.forward(request, response);
		//rq.fo
		//return Response.ok(new File(path)).build();

	}
//
//	@GET
//	@Path(PP_PATH)
//	public String getPath(@PathParam("path") String path) {
//		String[] comps = path.split("/");
//
//		return path;
//	}

}
