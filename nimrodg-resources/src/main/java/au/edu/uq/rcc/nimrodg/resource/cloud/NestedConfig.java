package au.edu.uq.rcc.nimrodg.resource.cloud;

import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;

import java.security.KeyPair;
import java.util.Objects;

public class NestedConfig extends SSHResourceType.SSHConfig {
	public final KeyPair keyPair;

	protected NestedConfig(SSHResourceType.SSHConfig cfg, KeyPair keyPair) {
		super(cfg);
		this.keyPair = Objects.requireNonNull(keyPair, "keyPair");
	}
}
