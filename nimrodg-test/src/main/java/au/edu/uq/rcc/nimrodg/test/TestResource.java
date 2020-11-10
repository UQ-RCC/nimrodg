package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceType;

import javax.json.JsonStructure;

/**
 * Used to get a {@link Resource} without an {@link au.edu.uq.rcc.nimrodg.api.NimrodAPI} instance.
 */
public class TestResource implements Resource {

	private final String name;
	private final ResourceType type;
	private final NimrodURI amqpUri;
	private final NimrodURI txUri;
	private final JsonStructure config;

	public TestResource(String name, ResourceType type, NimrodURI amqpUri, NimrodURI txUri, JsonStructure config) {
		this.name = name;
		this.type = type;
		this.amqpUri = amqpUri;
		this.txUri = txUri;
		this.config = config;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getTypeName() {
		return this.type.getName();
	}

	@Override
	public ResourceType getType() {
		return this.type;
	}

	@Override
	public JsonStructure getConfig() {
		return config;
	}

	@Override
	public NimrodURI getAMQPUri() {
		return this.amqpUri;
	}

	@Override
	public NimrodURI getTransferUri() {
		return this.txUri;
	}
}