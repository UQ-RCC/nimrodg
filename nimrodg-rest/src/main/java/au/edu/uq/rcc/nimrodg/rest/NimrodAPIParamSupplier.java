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

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.glassfish.hk2.api.Factory;

public class NimrodAPIParamSupplier implements Factory<NimrodAPI> {

	@Context
	private Configuration config;

	@Override
	public NimrodAPI provide() {
		NF nf = (NF)config.getProperty("nimrodg-config");
		if(nf == null) {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
		try {
			return nf.create();
		} catch(Exception ex) {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
	}

	@Override
	public void dispose(NimrodAPI t) {
		if(t != null) {
			try {
				t.close();
			} catch(Exception ex) {
				// nop
			}
		}
	}

}
