package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.cluster.pbs.PBSProDialect;
import au.edu.uq.rcc.nimrodg.resource.cluster.pbs.PBSResourceType;
import java.io.IOException;
import java.security.cert.Certificate;

public class TemplateClusterActuatorResourceType extends PBSResourceType {

	public TemplateClusterActuatorResourceType() {
		super("pbspro", "PBSPro", new PBSProDialect());
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig cfg) throws IOException {
		return new TemplateClusterActuator(ops, node, amqpUri, certs, cfg);
	}
}
