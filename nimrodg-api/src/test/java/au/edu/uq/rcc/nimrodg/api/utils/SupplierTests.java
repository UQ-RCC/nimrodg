package au.edu.uq.rcc.nimrodg.api.utils;

import au.edu.uq.rcc.nimrodg.api.utils.run.suppliers.ValueSupplier;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SupplierTests {

	@Test
	public void supplierTests() {
		testSupplier(ValueSupplier.getEmpty(), List.of());
		testSupplier(ValueSupplier.createDefaultSupplier("test"), List.of("test"));

		List<String> supplied = List.of("x", "y", "z");
		testSupplier(ValueSupplier.createSuppliedSupplier(supplied), supplied);

		ValueSupplier s = ValueSupplier.createIntegerRangeStepSupplier(1, 10, 1);
		testSupplier(s, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));

		testSupplier(ValueSupplier.createIntegerRangeStepSupplier(1, 10, 100), List.of("1"));

		long seed = 0;
		Random rnd = new Random(seed);

		/* Random may give different results on different implementations, so generate our expected first. */
		List<String> expLongRnd = rnd.longs(5, 1, 100)
				.mapToObj(String::valueOf).collect(Collectors.toList());

		testSupplier(ValueSupplier.createIntegerRandomSupplier(1, 100, 5, seed), expLongRnd);

		rnd.setSeed(seed);
		List<String> expDoubleRnd = rnd.doubles(5, 1.0, 100.0)
				.mapToObj(String::valueOf).collect(Collectors.toList());

		testSupplier(ValueSupplier.createFloatRandomSupplier(1.0, 100.0, 5, seed), expDoubleRnd);
	}

	private void testSupplier(ValueSupplier s, List<String> expectedValues) {
		testSupplier(s, expectedValues, true);
	}

	private void testSupplier(ValueSupplier s, List<String> expectedValues, boolean testDup) {
		Assert.assertEquals(expectedValues.size(), s.getTotalCount());

		ArrayList<String> valuesByGet = new ArrayList<>(expectedValues.size());
		ArrayList<String> valuesByGetAt = new ArrayList<>(expectedValues.size());
		ArrayList<String> valuesByGetAfterReset = new ArrayList<>(expectedValues.size());
		ArrayList<String> valuesByGetAtAfterReset = new ArrayList<>(expectedValues.size());

		/* Interleave get() and getAt(). getAt() shouldn't have any effect on get() and vice versa. */
		for(int i = 0; i < expectedValues.size(); ++i) {
			valuesByGet.add(s.get());
			valuesByGetAt.add(s.getAt(i));
		}

		s.reset();
		for(int i = 0; i < expectedValues.size(); ++i) {
			valuesByGetAfterReset.add(s.get());
			valuesByGetAtAfterReset.add(s.getAt(i));
		}

		Assert.assertEquals(expectedValues, valuesByGet);
		Assert.assertEquals(expectedValues, valuesByGetAt);
		Assert.assertEquals(expectedValues, valuesByGetAfterReset);
		Assert.assertEquals(expectedValues, valuesByGetAtAfterReset);
		Assert.assertEquals(expectedValues, ValueSupplier.stream(s).collect(Collectors.toList()));

		if(testDup) {
			testSupplier(s.duplicateFromStart(), expectedValues, false);
		}
	}
}
