package au.edu.uq.rcc.nimrodg.master.sig;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.Mac;

public class NullMac implements Mac {

	@Override
	public void init(CipherParameters params) {

	}

	@Override
	public String getAlgorithmName() {
		return null;
	}

	@Override
	public int getMacSize() {
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
