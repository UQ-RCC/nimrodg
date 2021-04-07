package au.edu.uq.rcc.nimrodg.resource.cloud;

import au.edu.uq.rcc.nimrodg.resource.ssh.ClientFactories;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;

import javax.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CloudSshFactory implements TransportFactory {

	public final KeyPair keyPair;

	public CloudSshFactory(KeyPair keyPair) {
		this.keyPair = Objects.requireNonNull(keyPair, "keyPair");
	}

	@Override
	public RemoteShell create(Config cfg, Path workDir) throws IOException {
		URI uri = cfg.uri.orElseThrow();
		return new SshdClient(uri, cfg.hostKeys, keyPair);
	}

	@Override
	public Optional<Config> validateConfiguration(JsonObject cfg, List<String> errors) {
		return ClientFactories.SSHD_FACTORY.validateConfiguration(cfg, errors);
	}

	@Override
	public Config resolveConfiguration(Config cfg) throws IOException {
		return ClientFactories.SSHD_FACTORY.resolveConfiguration(cfg);
	}


	@Override
	public JsonObject buildJsonConfiguration(Config cfg) {
		return ClientFactories.SSHD_FACTORY.buildJsonConfiguration(cfg);
	}
}
