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

import java.nio.file.Path;
import net.vs49688.rafview.cli.webdav.NIOResourceRoot;
import org.apache.catalina.WebResource;
import org.apache.catalina.webresources.EmptyResource;

public class NimrodResourceRoot extends NIOResourceRoot {

	public NimrodResourceRoot(String webAppPath, Path rootPath) {
		super(webAppPath, rootPath);
	}

	@Override
	public WebResource getResource(String path) {
		logger.debug("Request for resource: %s", path);
		if(path.startsWith("/META-INF") || path.startsWith("/WEB-INF")) {
			logger.debug("  Forbidden path, returning empty");
			return new EmptyResource(this, path);
		}

		return super.getResource(path);
	}

	@Override
	public WebResource[] listResources(String path) {
		if(path.startsWith("/META-INF") || path.startsWith("/WEB-INF")) {
			logger.debug("  Forbidden path, returning empty");
			return new WebResource[0];
		}

		return super.listResources(path);
	}
}
