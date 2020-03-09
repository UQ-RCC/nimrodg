package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Random;

public class RandomDoubleSupplier extends IteratorSupplier<Double> {
	private final double start;
	private final double end;
	private final long seed;
	private final Random random;

	RandomDoubleSupplier(double start, double end, int count, long seed) {
		this(start, end, count, seed, new Random(seed));
	}

	private RandomDoubleSupplier(double start, double end, int count, long seed, Random random) {
		super(random.doubles(count, start, end).iterator(), count);
		this.start = start;
		this.end = end;
		this.seed = seed;
		this.random = random;
	}

	@Override
	public ValueSupplier _duplicate() {
		return new RandomDoubleSupplier(start, end, count, seed);
	}

	@Override
	public void reset() {
		random.setSeed(seed);
		it = random.doubles(count, start, end).iterator();
	}
}
