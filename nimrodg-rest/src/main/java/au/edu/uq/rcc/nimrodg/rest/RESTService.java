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
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import javax.ws.rs.core.GenericType;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.startup.Tomcat;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

public class RESTService implements AutoCloseable {

	private final Service m_Service;
	private final Server m_Server;
	private final Engine m_Engine;
	private final Host m_Host;
	private final Context m_Context;
	private final Connector m_Connector;
	private final NF m_NF;

	public RESTService(String base, String hostname, int port, NimrodAPIFactory factory, UserConfig config) throws LifecycleException, IOException, Exception {
		if(base == null) {
			throw new IllegalArgumentException();
		}
		if(port <= 0 || port > 65535) {
			throw new IllegalArgumentException();
		}

		m_NF = new NF(factory, config);

		Path nimrodHome;
		try(NimrodAPI napi = m_NF.create()) {
			nimrodHome = Paths.get(napi.getConfig().getWorkDir());
		}
		Path catalinaHome = nimrodHome.resolve("fileserver-tmp");
		if(!Files.exists(catalinaHome)) {
			Files.createDirectories(catalinaHome);
		}

		catalinaHome = catalinaHome.toAbsolutePath();

		System.setProperty("catalina.useNaming", "false");
		System.setProperty(Globals.CATALINA_HOME_PROP, catalinaHome.toString());

		m_Server = new StandardServer();
		m_Server.setPort(-1);
		m_Server.setCatalinaHome(catalinaHome.toFile());

		m_Engine = new StandardEngine();
		m_Engine.setName("NimrodREST");
		m_Engine.setDefaultHost(hostname);

		m_Host = new StandardHost();
		m_Host.setName(hostname);
		m_Engine.addChild(m_Host);

		m_Service = new StandardService();
		m_Service.setName("NimrodREST");
		m_Service.setContainer(m_Engine);
		m_Server.addService(m_Service);

		m_Context = new StandardContext();
		m_Context.setName(base);
		m_Context.setPath(base);
		m_Context.setDocBase(catalinaHome.toString());
		m_Context.addLifecycleListener(new Tomcat.FixContextListener());

		m_Host.addChild(m_Context);

		Wrapper sw = new Tomcat.ExistingStandardWrapper(resourceConfig());
		sw.setName("nimrodg-rest");
		m_Context.addChild(sw);

		m_Context.addServletMappingDecoded("/api/*", "nimrodg-rest");

		{
//			Context davCtx = new StandardContext();
//			davCtx.setName("nimrodg-dav");
//			davCtx.setPath("/storage");
//			davCtx.setDocBase("I:/");
//			davCtx.addLifecycleListener(new Tomcat.FixContextListener());
//			m_Host.addChild(davCtx);
//

			Wrapper dw = new Tomcat.ExistingStandardWrapper(new WebDAVServlet(m_NF));
			dw.setName("nimrodg-dav");
			m_Context.addChild(dw);
			m_Context.addServletMappingDecoded("/dav/*", "nimrodg-dav");
		}

		m_Context.setResources(new NimrodResourceRoot("", nimrodHome.resolve("experiments")));

		m_Connector = new Connector("HTTP/1.1");
		m_Connector.setPort(port);
		m_Service.addConnector(m_Connector);

		m_Server.init();
		m_Server.start();
	}

	public void await() {
		m_Server.await();
	}

	public void stop() throws LifecycleException {
		m_Server.stop();
	}

	@Override
	public void close() throws LifecycleException {
		m_Server.stop();
		m_Server.destroy();
	}

	private ServletContainer resourceConfig() {
		ResourceConfig cfg = new ResourceConfig(new ApplicationConfig().getClasses());
		cfg.register(new AbstractBinder() {
			@Override
			protected void configure() {

				// https://stackoverflow.com/a/49328601
				this.bindFactory(NimrodAPIParamSupplier.class)
						.to(NimrodAPI.class)
						.proxy(true)
						.proxyForSameScope(true)
						.in(RequestScoped.class);

				this.bind(NimrodAPIParamResolver.class)
						.to(new GenericType<InjectionResolver<NimrodAPIParam>>() {
						}.getType())
						.in(Singleton.class);
			}
		});

		Map<String, Object> props = new HashMap<>();
		props.put("nimrodg-config", m_NF);
		cfg.addProperties(props);
		return new ServletContainer(cfg);
	}
}
