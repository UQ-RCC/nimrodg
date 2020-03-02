package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Random;
import java.util.stream.DoubleStream;

public class RandomDoubleSupplier implements ValueSupplier {
	private final int count;
	private final DoubleStream doubleStream;

	RandomDoubleSupplier(double start, double end, int count) {
		this.count = count;
		this.doubleStream = new Random().doubles(count, start, end);
	}

	@Override
	public int getTotalCount() {
		return count;
	}

	@Override
	public String get() {
		double val = doubleStream.findFirst().orElseThrow(IllegalStateException::new);
		return Double.toString(val);
	}
}
