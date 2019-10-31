package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.NimrodConfig;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;

import java.net.URI;
import java.nio.file.Path;

public class TestNimrodConfig implements NimrodConfig {
	public final Path path;

	public TestNimrodConfig(Path path) {
		this.path = path;
	}

	@Override
	public String getWorkDir() {
		return path.toString();
	}

	@Override
	public String getRootStore() {
		return path.resolve("experiments/").toString();
	}

	@Override
	public NimrodURI getAmqpUri() {
		return NimrodURI.create(URI.create("amqps://user:pass@hostname/vhost"), "", false, false);
	}

	@Override
	public String getAmqpRoutingKey() {
		return "iamthemaster";
	}

	@Override
	public NimrodURI getTransferUri() {
		return NimrodURI.create(URI.create("http://localhost:8080/storage/"), "", false, false);
	}
}
