package au.edu.uq.rcc.nimrodg.api.setup;

import java.net.URI;

public final class AMQPConfig {
	private final URI uri;
	private final String routingKey;
	private final String certPath;
	private final boolean noVerifyPeer;
	private final boolean noVerifyHost;

	AMQPConfig(URI uri, String routingKey, String certPath, boolean noVerifyPeer, boolean noVerifyHost) {
		this.uri = uri;
		this.routingKey = routingKey;
		this.certPath = certPath;
		this.noVerifyPeer = noVerifyPeer;
		this.noVerifyHost = noVerifyHost;
	}

	public URI uri() {
		return this.uri;
	}

	public String routingKey() {
		return this.routingKey;
	}

	public String certPath() {
		return this.certPath;
	}

	public boolean noVerifyPeer() {
		return this.noVerifyPeer;
	}

	public boolean noVerifyHost() {
		return this.noVerifyHost;
	}
}
