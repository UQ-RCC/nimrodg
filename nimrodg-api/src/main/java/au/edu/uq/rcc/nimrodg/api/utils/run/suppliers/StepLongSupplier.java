package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class StepLongSupplier implements ValueSupplier {
	private final int totalCount;
	private final long start;
	private final long end;
	private final int step;
	private long next;


	public StepLongSupplier(long start, long end, int step) {
		this.totalCount = (int)Math.max(0, (end - start) / step) + 1;
		this.start = start;
		this.end = end;
		this.step = step;
		this.next = start;
	}

	@Override
	public int getTotalCount() {
		return totalCount;
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return new StepLongSupplier(start, end, step);
	}

	@Override
	public void reset() {
		next = start;
	}

	@Override
	public String get() {
		if(next > end) {
			throw new IllegalStateException();
		}

		long val = next;
		next += step;
		return String.valueOf(val);
	}

	@Override
	public boolean isFastIndex() {
		return true;
	}

	@Override
	public String getAt(int i) {
		if(i > totalCount) {
			throw new IllegalArgumentException();
		}

		return String.valueOf(start + (step * i));
	}
}
