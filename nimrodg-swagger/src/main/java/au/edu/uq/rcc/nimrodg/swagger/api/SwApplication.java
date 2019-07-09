package au.edu.uq.rcc.nimrodg.swagger.api;

import io.swagger.jaxrs.config.BeanConfig;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/v1")
public class SwApplication extends Application {

	public SwApplication() {
		BeanConfig beanConfig = new BeanConfig();
		beanConfig.setVersion("1.0");
		//beanConfig.setSchemes(new String[]{"http"});
		//beanConfig.setHost("localhost:8080");
		beanConfig.setBasePath("/v1");
		beanConfig.setResourcePackage("io.swagger.resources");
		beanConfig.setResourcePackage("au.edu.uq.rcc.nimrodg.swagger.api");
		beanConfig.setScan(true);
	}

	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> resources = new HashSet<>();

		resources.add(io.swagger.jaxrs.listing.ApiListingResource.class);
		resources.add(io.swagger.jaxrs.listing.SwaggerSerializers.class);

		/* Add all the generated XxxApi classes here. */
		resources.add(au.edu.uq.rcc.nimrodg.swagger.api.AgentApi.class);
		resources.add(au.edu.uq.rcc.nimrodg.swagger.api.ConfigApi.class);

		return resources;
	}
}
