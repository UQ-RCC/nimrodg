package au.edu.uq.rcc.nimrodg.setup;

import java.net.URI;

public final class TransferConfigBuilder {

	private URI uri;
	private String certPath;
	private boolean noVerifyPeer;
	private boolean noVerifyHost;

	public TransferConfigBuilder uri(URI uri) {
		this.uri = uri;
		return this;
	}

	public TransferConfigBuilder certPath(String certPath) {
		this.certPath = certPath;
		return this;
	}

	public TransferConfigBuilder noVerifyPeer(boolean b) {
		this.noVerifyPeer = b;
		return this;
	}

	public TransferConfigBuilder noVerifyHost(boolean b) {
		this.noVerifyHost = b;
		return this;
	}

	public void clear() {
		uri = null;
		certPath = null;
		noVerifyPeer = false;
		noVerifyHost = false;
	}

	public TransferConfig build() {
		if(uri == null) {
			throw new IllegalArgumentException();
		}

		if(certPath == null) {
			certPath = "";
		}

		TransferConfig cfg = new TransferConfig(uri, certPath, noVerifyPeer, noVerifyHost);
		clear();
		return cfg;
	}
}
