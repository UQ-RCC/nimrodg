package au.edu.uq.rcc.nimrodg.resource.ssh;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SSHTunnel {

	public final boolean remote;
	public final String srcHost;
	public final int srcPort;
	public final String dstHost;
	public final int dstPort;

	private static final Pattern PATTERN = Pattern.compile("^(?:(.+):)?(\\d+):(.+):(\\d+)$");

	public SSHTunnel(boolean remote, String srcHost, int srcPort, String dstHost, int dstPort) {
		this.remote = remote;
		this.srcHost = srcHost;
		this.srcPort = srcPort;
		this.dstHost = dstHost;
		this.dstPort = dstPort;
	}

	public static SSHTunnel fromString(boolean remote, String s) {
		Matcher m = PATTERN.matcher(s);
		if(!m.matches()) {
			throw new IllegalArgumentException();
		}

		String srcHost = m.group(1);
		if(srcHost == null)
			srcHost = "";

		return new SSHTunnel(
				remote,
				srcHost,
				Integer.parseUnsignedInt(m.group(2)),
				m.group(3),
				Integer.parseUnsignedInt(m.group(4))
		);
	}
}
