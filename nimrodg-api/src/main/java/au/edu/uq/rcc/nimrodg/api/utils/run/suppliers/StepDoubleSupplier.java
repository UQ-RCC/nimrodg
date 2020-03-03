package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class StepDoubleSupplier implements ValueSupplier {
	private final int totalCount;
	private double start;
	private double end;
	private double step;
	private double next;

	public StepDoubleSupplier(double start, double end, double step) {
		this.totalCount = (int)Math.ceil(Math.max(0, (end - start) / step));
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
		return new StepDoubleSupplier(start, end, step);
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

		double val = next;
		next += step;
		return String.valueOf(val);
	}
}
