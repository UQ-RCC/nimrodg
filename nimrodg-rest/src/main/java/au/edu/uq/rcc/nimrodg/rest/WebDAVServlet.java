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
import au.edu.uq.rcc.nimrodg.api.NimrodEntity;
import au.edu.uq.rcc.nimrodg.api.NimrodServeAPI;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebDAVServlet extends WebdavServlet {

	private static final Logger LOGGER = LogManager.getLogger(WebDAVServlet.class);

	private final Pattern pathPattern;
	private final Pattern uaPattern;
	private final NF nf;

	public WebDAVServlet(NF nf) {
		this.pathPattern = Pattern.compile("^\\/([a-zA-Z_][a-zA-Z0-9_]+)(\\/.*|)?$");
		this.uaPattern = Pattern.compile("^NimrodG\\/(.+)\\s+\\((.+)\\)$");
		this.nf = nf;
	}

	@Override
	public void init() throws ServletException {
		super.init();

		this.listings = true;
		//this.debug = 1;
		this.readOnly = false;
	}

	private static String getHostString(HttpServletRequest request) {
		return String.format("%s:%d", request.getRemoteAddr(), request.getRemotePort());
	}

	private class RequestInfo {

		public String requestPath;
		public String experiment;
		public String filePath;

		public String headerUserAgent;
		public boolean validAgentUserAgent;
		public String agentVersion;
		public String agentPlatform;

		public String headerAgentUuid;
		public UUID agentUuid;

		public String headerAuthToken;

	}

	private static void aaaaa(HttpServletRequest request, HttpServletResponse response, Matcher m, String uriPath) throws IOException {
		String hostString = getHostString(request);

		LOGGER.trace("[{}]: Agent request to {}", hostString, uriPath);
		LOGGER.trace("[{}]:   version  = {}", hostString, m.group(1));
		LOGGER.trace("[{}]:   platform = {}", hostString, m.group(2));

		String _agentUuid = request.getHeader("X-NimrodG-Agent-UUID");
		if(_agentUuid == null) {
			LOGGER.info("[{}]: Request to {} without agent uuid", getHostString(request), uriPath);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		UUID agentUuid;
		try {
			agentUuid = UUID.fromString(_agentUuid);
		} catch(IllegalArgumentException e) {
			LOGGER.info("[{}]: Request to {} with invalid agent uuid", getHostString(request), uriPath);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		LOGGER.trace("[{}]:   uuid     = {}", hostString, agentUuid);

		String token = request.getHeader("X-NimrodG-File-Auth-Token");
		if(token == null) {
			LOGGER.info("[{}]: Request to {} without token", getHostString(request), uriPath);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String hostString = getHostString(request);
		String uriPath = request.getPathInfo();

		if(uriPath == null) {
			uriPath = "/";
		}

		Matcher m = pathPattern.matcher(uriPath);
		if(!m.matches()) {
			LOGGER.trace("[{}]: Invalid request to {}", hostString, uriPath);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		RequestInfo ri = new RequestInfo();
		{
			/* Gather all the request information. */
			ri.requestPath = uriPath;
			ri.experiment = m.group(1);
			ri.filePath = m.group(2);

			if((ri.headerUserAgent = request.getHeader("User-Agent")) != null) {
				m = uaPattern.matcher(ri.headerUserAgent);
				if((ri.validAgentUserAgent = m.matches())) {
					ri.agentVersion = m.group(1);
					ri.agentPlatform = m.group(2);
				}
			}

			if((ri.headerAgentUuid = request.getHeader("X-NimrodG-Agent-UUID")) != null) {
				try {
					ri.agentUuid = UUID.fromString(ri.headerAgentUuid);
				} catch(IllegalArgumentException e) {
					ri.agentUuid = null;
				}
			}

			ri.headerAuthToken = request.getHeader("X-NimrodG-File-Auth-Token");
		}

		LOGGER.trace("[{}]: Request to path '{}' in experiment '{}'", getHostString(request), ri.filePath, ri.experiment);

		if(ri.validAgentUserAgent) {
			/* We're an agent (or pretending to be) */
			LOGGER.trace("[{}]: Agent request to {}", hostString, ri.requestPath);
			LOGGER.trace("[{}]:   version  = {}", hostString, ri.agentVersion);
			LOGGER.trace("[{}]:   platform = {}", hostString, ri.agentPlatform);

			if(ri.agentUuid == null) {
				LOGGER.info("[{}]: Request to {} with no or invalid agent uuid", hostString, uriPath);
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			LOGGER.trace("[{}]:   uuid     = {}", hostString, ri.agentUuid);

			if(ri.headerAuthToken == null || ri.headerAuthToken.isEmpty()) {
				LOGGER.info("[{}]: Request to {} with no or invalid token", hostString, uriPath);
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		} else {
			/* We're another WebDAV client */
			// TODO: Handle this
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		NimrodAPI _api;
		try {
			_api = nf.create();
		} catch(Exception e) {
			LOGGER.error("[{}]: Nimrod API instantiation failure", hostString);
			LOGGER.catching(e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		if(!_api.getAPICaps().serve) {
			LOGGER.error("[{}]: Nimrod API instantiation does not provide serve capabilities", hostString);
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		try(NimrodServeAPI api = (NimrodServeAPI)_api) {

			Experiment exp = api.getExperiment(ri.experiment);
			if(exp == null) {
				LOGGER.trace("[{}]: Request to invalid experiment '{}'", hostString, ri.experiment);
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}

			NimrodEntity ent = api.isTokenValidForStorage(exp, ri.headerAuthToken);
			if(ent == null) {
				LOGGER.info("[{}]: Invalid authentication to {}", hostString, uriPath);
				//response.sendError(HttpServletResponse.SC_FORBIDDEN);
				response.setHeader("WWW-Authenticate", "Basic realm=\"Nimrod\" charset=\"UTF-8\"");
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}

			if(ent.equals(exp)) {
				// Is a run token
				LOGGER.trace("[{}]: Successful experiment-level authentication to {}", hostString, uriPath);
			} else {
				// Is a attempt token
				LOGGER.trace("[{}]: Successful attempt-level authentication to {}", hostString, uriPath);
			}
		} catch(Exception e) {
			LOGGER.trace("[{}]: Nimrod API close failure", hostString);
			LOGGER.catching(e);
		}
		super.service(request, response);

	}

}
