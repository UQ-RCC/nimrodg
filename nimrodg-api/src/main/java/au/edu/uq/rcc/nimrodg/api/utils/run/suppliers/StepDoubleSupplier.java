package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

public class StepDoubleSupplier implements ValueSupplier {
	private final int totalCount;
	private double step;
	private double next;
	private double end;

	public StepDoubleSupplier(double start, double end, double step) {
		this.totalCount = (int)Math.ceil(Math.max(0, (end - start) / step));
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

		double val = next;
		next += step;
		return String.valueOf(val);
	}
}
