package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Random;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class RandomLongSupplier implements ValueSupplier {
	private final long start;
	private final long end;
	private final int count;
	private final long seed;
	private final Random random;
	private LongStream longStream;

	RandomLongSupplier(long start, long end, int count, long seed) {
		this.start = start;
		this.end = end;
		this.count = count;
		this.seed = seed;
		this.random = new Random(seed);
		this.longStream = random.longs(count, start, end);
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
		longStream = random.longs(count, start, end);
	}

	@Override
	public String get() {
		long val = longStream.findFirst().orElseThrow(IllegalStateException::new);
		return Long.toString(val);
	}
}
