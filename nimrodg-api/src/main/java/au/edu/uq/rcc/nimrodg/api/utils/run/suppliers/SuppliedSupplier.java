package au.edu.uq.rcc.nimrodg.api.utils.run.suppliers;

import java.util.Iterator;
import java.util.List;

public class SuppliedSupplier<T> implements ValueSupplier {
	private final List<T> source;
	private Iterator<T> it;

	SuppliedSupplier(List<T> source) {
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

	@Override
	public String getAt(int idx) {
		if(idx >= source.size()) {
			throw new IllegalArgumentException();
		}

		return source.get(idx).toString();
	}
}
