package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Iterator;
import java.util.Objects;

public abstract class IteratorSupplier<T> implements ValueSupplier {
	protected final int count;

	protected Iterator<T> it;

	protected IteratorSupplier(Iterator<T> it, int count) {
		this.count = count;
		this.it = it;
	}

	@Override
	public final int getTotalCount() {
		return count;
	}

	@Override
	public abstract ValueSupplier duplicateFromStart();

	@Override
	public abstract void reset();

	@Override
	public final String get() {
		if(!it.hasNext()) {
			throw new IllegalStateException();
		}

		return Objects.toString(it.next());
	}
}
