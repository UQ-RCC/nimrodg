package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Random;

public class RandomLongSupplier extends IteratorSupplier<Long> {
	private final long start;
	private final long end;
	private final long seed;
	private final Random random;

	RandomLongSupplier(long start, long end, int count, long seed) {
		this(start, end, count, seed, new Random(seed));
	}

	private RandomLongSupplier(long start, long end, int count, long seed, Random random) {
		super(random.longs(count, start, end).iterator(), count);
		this.start = start;
		this.end = end;
		this.seed = seed;
		this.random = new Random(seed);
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return new RandomLongSupplier(start, end, count, seed);
	}

	@Override
	public void reset() {
		random.setSeed(seed);
		it = random.longs(count, start, end).iterator();
	}
}
