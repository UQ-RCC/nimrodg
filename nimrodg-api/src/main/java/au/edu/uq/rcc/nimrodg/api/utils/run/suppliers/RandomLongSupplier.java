package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Iterator;
import java.util.Random;

public class RandomLongSupplier implements ValueSupplier {
	private final long start;
	private final long end;
	private final int count;
	private final long seed;
	private final Random random;
	private Iterator<Long> it;

	RandomLongSupplier(long start, long end, int count, long seed) {
		this.start = start;
		this.end = end;
		this.count = count;
		this.seed = seed;
		this.random = new Random(seed);
		this.it = random.longs(count, start, end).iterator();
	}

	@Override
	public int getTotalCount() {
		return count;
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

	@Override
	public String get() {
		if(!it.hasNext()) {
			throw new IllegalStateException();
		}

		return it.next().toString();
	}
}
