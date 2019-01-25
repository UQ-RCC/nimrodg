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

import javax.ws.rs.ext.*;
import org.glassfish.jersey.moxy.json.*;

@Provider
public class MOXYContextResolver implements ContextResolver<MoxyJsonConfig> {

	private final MoxyJsonConfig m_Context;

	public MOXYContextResolver() throws Exception {

		m_Context = new MoxyJsonConfig(true);
		m_Context.setIncludeRoot(true);
	}

	@Override
	public MoxyJsonConfig getContext(Class<?> objectType) {
		return m_Context;
	}
}
