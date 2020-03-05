package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class EmptySupplier implements ValueSupplier {

	public static final EmptySupplier INSTANCE = new EmptySupplier();

	private EmptySupplier() {

	}

	@Override
	public int getTotalCount() {
		return 0;
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return this;
	}

	@Override
	public void reset() {

	}

	@Override
	public String get() {
		throw new IllegalStateException();
	}

	@Override
	public boolean isFastIndex() {
		return true;
	}

	@Override
	public String getAt(int i) {
		throw new IllegalArgumentException();
	}
}
