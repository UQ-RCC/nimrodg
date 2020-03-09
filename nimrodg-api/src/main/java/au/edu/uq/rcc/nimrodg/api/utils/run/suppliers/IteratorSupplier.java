package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Iterator;
import java.util.Objects;

public abstract class IteratorSupplier<T> implements ValueSupplier {
	protected final int count;

	protected Iterator<T> it;
	private ValueSupplier cache;

	protected IteratorSupplier(Iterator<T> it, int count) {
		this.count = count;
		this.it = it;
		this.cache = null;
	}

	@Override
	public final int getTotalCount() {
		return count;
	}

	@Override
	public final ValueSupplier duplicateFromStart() {
		if(cache != null) {
			return cache.duplicateFromStart();
		}

		return _duplicate();
	}

	protected abstract ValueSupplier _duplicate();

	@Override
	public abstract void reset();

	@Override
	public final String get() {
		if(!it.hasNext()) {
			throw new IllegalStateException();
		}

		return Objects.toString(it.next());
	}

	@Override
	public String getAt(int i) {
		/* getAt() on iterators is slow, so expand and cache if we're called. */
		if(cache == null) {
			cache = ValueSupplier.expandToSupplied(duplicateFromStart());
		}

		return cache.getAt(i);
	}
}
