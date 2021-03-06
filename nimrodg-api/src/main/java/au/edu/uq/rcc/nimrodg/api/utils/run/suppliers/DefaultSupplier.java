package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class DefaultSupplier implements ValueSupplier {
	private final String value;
	private boolean retrieved;

	DefaultSupplier(String value) {
		this.value = value;
		this.retrieved = false;
	}

	DefaultSupplier(long value) {
		this(String.valueOf(value));
	}

	DefaultSupplier(double value) {
		this(String.valueOf(value));
	}

	@Override
	public int getTotalCount() {
		return 1;
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return new DefaultSupplier(value);
	}

	@Override
	public void reset() {
		retrieved = false;
	}

	@Override
	public String get() {
		if(retrieved) {
			throw new IllegalStateException();
		}
		return value;
	}

	@Override
	public String getAt(int i) {
		if(i != 0) {
			throw new IllegalArgumentException();
		}

		return value;
	}
}
