package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class StepLongSupplier implements ValueSupplier {
	private final int totalCount;
	private final int step;
	private long next;
	private long end;

	public StepLongSupplier(long start, long end, int step) {
		this.totalCount = (int)Math.max(0, (end - start) / step) + 1;
		this.step = step;
		this.next = start;
		this.end = end;
	}

	@Override
	public int getTotalCount() {
		return totalCount;
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
}
