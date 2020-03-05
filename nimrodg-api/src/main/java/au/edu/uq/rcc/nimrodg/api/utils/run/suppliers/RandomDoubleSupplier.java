package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Iterator;
import java.util.Random;
import java.util.stream.DoubleStream;

public class RandomDoubleSupplier implements ValueSupplier {
	private final double start;
	private final double end;
	private final int count;
	private final long seed;
	private final Random random;
	private Iterator<Double> it;

	RandomDoubleSupplier(double start, double end, int count, long seed) {
		this.start = start;
		this.end = end;
		this.count = count;
		this.seed = seed;
		this.random = new Random(seed);
		this.it = random.doubles(count, start, end).iterator();
	}

	@Override
	public int getTotalCount() {
		return count;
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return new RandomDoubleSupplier(start, end, count, seed);
	}

	@Override
	public void reset() {
		random.setSeed(seed);
		it = random.doubles(count, start, end).iterator();
	}

	@Override
	public String get() {
		if(!it.hasNext()) {
			throw new IllegalStateException();
		}

		return it.next().toString();
	}
}
