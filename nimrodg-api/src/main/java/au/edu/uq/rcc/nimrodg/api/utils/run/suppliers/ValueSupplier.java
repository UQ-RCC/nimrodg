package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.security.SecureRandom;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ValueSupplier extends Supplier<String> {

	int getTotalCount();

	ValueSupplier duplicateFromStart();

	void reset();

	@Override
	String get();

	/**
	 * Does {@link #getAt(int)} run in constant time.
	 * @return true, if {@link #getAt(int)} runs in constant time. Otherwise, false.
	 */
	boolean isFastIndex();

	default String getAt(int i) {
		/*
		 * Naive implementation, subclasses are expected to provide
		 * a more efficient version, if possible.
		 */
		return ValueSupplier.stream(this).skip(i).findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}

	static Stream<String> stream(ValueSupplier s) {
		return Stream.generate(s.duplicateFromStart()).limit(s.getTotalCount());
	}

	static ValueSupplier expandToSupplied(ValueSupplier s) {
		return createSuppliedSupplier(ValueSupplier.stream(s).collect(Collectors.toUnmodifiableList()));
	}

	static ValueSupplier createDefaultSupplier(long value) {
		return new DefaultSupplier(value);
	}

	static ValueSupplier createDefaultSupplier(double value) {
		return new DefaultSupplier(value);
	}

	static ValueSupplier createDefaultSupplier(String value) {
		return new DefaultSupplier(value);
	}

	static ValueSupplier createIntegerRangeStepSupplier(long start, long end, int step) {
		return new StepLongSupplier(start, end, step);
	}

	private static long getSeed() {
		byte[] _seed = SecureRandom.getSeed(8);
		return _seed[0] |
				((_seed[1] & 0xFFL) << 8) |
				((_seed[2] & 0xFFL) << 16) |
				((_seed[3] & 0xFFL) << 24) |
				((_seed[4] & 0xFFL) << 32) |
				((_seed[5] & 0xFFL) << 40) |
				((_seed[6] & 0xFFL) << 48) |
				((_seed[7] & 0xFFL) << 56);
	}

	static ValueSupplier createIntegerRandomSupplier(long start, long end, int count) {
		return createIntegerRandomSupplier(start, end, count, getSeed());
	}

	static ValueSupplier createIntegerRandomSupplier(long start, long end, int count, long seed) {
		return expandToSupplied(new RandomLongSupplier(start, end, count, seed));
	}

	static ValueSupplier createFloatRangeStepSupplier(double start, double end, double step) {
		return new StepDoubleSupplier(start, end, step);
	}

	static ValueSupplier createFloatRandomSupplier(double start, double end, int count) {
		return createFloatRandomSupplier(start, end, count, getSeed());
	}

	static ValueSupplier createFloatRandomSupplier(double start, double end, int count, long seed) {
		return expandToSupplied(new RandomDoubleSupplier(start, end, count, seed));
	}

	static <T> ValueSupplier createSuppliedSupplier(List<T> values) {
		return new SuppliedSupplier<>(values);
	}

	static ValueSupplier getEmpty() {
		return EmptySupplier.INSTANCE;
	}
}
