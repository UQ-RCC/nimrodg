package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Collection;
import java.util.Iterator;

public class SuppliedSupplier<T> implements ValueSupplier {
	private final Collection<T> source;
	private Iterator<T> it;

	SuppliedSupplier(Collection<T> source) {
		this.source = source;
		this.it = source.iterator();
	}

	@Override
	public int getTotalCount() {
		return source.size();
	}

	@Override
	public ValueSupplier duplicateFromStart() {
		return new SuppliedSupplier<>(source);
	}

	@Override
	public void reset() {
		it = source.iterator();
	}

	@Override
	public String get() {
		if(!it.hasNext()) {
			throw new IllegalStateException();
		}
		return it.next().toString();
	}
}
