package au.edu.uq.rcc.nimrodg.api.setup;

import java.net.URI;

public final class AMQPConfigBuilder {

	private URI uri;
	private String routingKey;
	private String certPath;
	private boolean noVerifyPeer;
	private boolean noVerifyHost;

	public AMQPConfigBuilder uri(URI uri) {
		this.uri = uri;
		return this;
	}

	public AMQPConfigBuilder routingKey(String routingKey) {
		this.routingKey = routingKey;
		return this;
	}

	public AMQPConfigBuilder certPath(String certPath) {
		this.certPath = certPath;
		return this;
	}

	public AMQPConfigBuilder noVerifyPeer(boolean b) {
		this.noVerifyPeer = b;
		return this;
	}

	public AMQPConfigBuilder noVerifyHost(boolean b) {
		this.noVerifyHost = b;
		return this;
	}

	public void clear() {
		uri = null;
		routingKey = null;
		certPath = null;
		noVerifyPeer = false;
		noVerifyHost = false;
	}

	public AMQPConfig build() {
		if(uri == null) {
			throw new IllegalArgumentException();
		}

		if(routingKey == null) {
			throw new IllegalArgumentException();
		}

		if(certPath == null) {
			certPath = "";
		}

		AMQPConfig cfg = new AMQPConfig(uri, routingKey, certPath, noVerifyPeer, noVerifyHost);
		clear();
		return cfg;
	}
}
