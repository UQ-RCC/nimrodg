package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.io.IOException;
import java.security.cert.Certificate;

public class TemplateClusterResourceType extends ClusterResourceType {

	public TemplateClusterResourceType(String name, String displayName, String argsName) {
		super(name, displayName, argsName);
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig cfg) throws IOException {
		return new TemplateClusterActuator(ops, node, amqpUri, certs, cfg);
	}
}
