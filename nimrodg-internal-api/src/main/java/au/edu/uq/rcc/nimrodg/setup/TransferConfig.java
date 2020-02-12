package au.edu.uq.rcc.nimrodg.setup;

import java.net.URI;

public final class TransferConfig {
	private final URI uri;
	private final String certPath;
	private final boolean noVerifyPeer;
	private final boolean noVerifyHost;

	TransferConfig(URI uri, String certPath, boolean noVerifyPeer, boolean noVerifyHost) {
		this.uri = uri;
		this.certPath = certPath;
		this.noVerifyPeer = noVerifyPeer;
		this.noVerifyHost = noVerifyHost;
	}

	public URI uri() {
		return this.uri;
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
