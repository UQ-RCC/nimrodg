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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
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
