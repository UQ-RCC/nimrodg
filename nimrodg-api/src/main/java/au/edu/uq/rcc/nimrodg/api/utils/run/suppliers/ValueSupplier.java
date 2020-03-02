package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface ValueSupplier extends Supplier<String> {

	int getTotalCount();

	@Override
	String get();

	default Stream<String> stream() {
		return Stream.generate(this).limit(getTotalCount());
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

	static ValueSupplier createIntegerRangePointsSupplier(long start, long end, int count) {
		return createIntegerRangeStepSupplier(start, end, (int)Math.max(0.0, (end - start) / (double)count));
	}

	static ValueSupplier createIntegerRandomSupplier(long start, long end, int count) {
		return new RandomLongSupplier(start, end, count);
	}

	static ValueSupplier createIntegerSuppliedSupplier(Collection<Long> values) {
		return new SuppliedSupplier<>(values);
	}


	static ValueSupplier createFloatRangeStepSupplier(double start, double end, double step) {
		return new StepDoubleSupplier(start, end, step);
	}

	static ValueSupplier createFloatRangePointsSupplier(double start, double end, int count) {
		return createFloatRangeStepSupplier(start, end, (int)Math.max(0.0, (end - start) / (double)count));
	}

	static ValueSupplier createFloatRandomSupplier(double start, double end, int count) {
		return new RandomDoubleSupplier(start, end, count);
	}

	static ValueSupplier createFloatSuppliedSupplier(Collection<Double> values) {
		return new SuppliedSupplier<>(values);
	}

	static ValueSupplier createStringSuppliedSupplier(Collection<String> values) {
		return new SuppliedSupplier<>(values);
	}
}
