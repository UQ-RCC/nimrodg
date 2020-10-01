package au.edu.uq.rcc.nimrodg.master.sig;

import org.bouncycastle.crypto.Digest;

public class MoreNullDigest implements Digest {
	@Override
	public String getAlgorithmName() {
		return "NULL";
	}

	@Override
	public int getDigestSize() {
		return 0;
	}

	@Override
	public void update(byte in) {

	}

	@Override
	public void update(byte[] in, int inOff, int len) {

	}

	@Override
	public int doFinal(byte[] out, int outOff) {
		return 0;
	}

	@Override
	public void reset() {

	}
}
