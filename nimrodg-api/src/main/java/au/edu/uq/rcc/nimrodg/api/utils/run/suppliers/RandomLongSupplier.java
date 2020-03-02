package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Random;
import java.util.stream.LongStream;

public class RandomLongSupplier implements ValueSupplier {
	private final int count;
	private final LongStream longStream;

	RandomLongSupplier(long start, long end, int count) {
		this.count = count;
		this.longStream = new Random().longs(count, start, end);
	}

	@Override
	public int getTotalCount() {
		return count;
	}

	@Override
	public String get() {
		long val = longStream.findFirst().orElseThrow(IllegalStateException::new);
		return Long.toString(val);
	}
}
