package au.edu.uq.rcc.nimrodg.master;

public interface ConfigListener {
	void onConfigChange(String key, String oldValue, String newValue);

	static long clamp(long val, long min, long max) {
		return Math.max(min, Math.min(max, val));
	}

	static int clamp(int val, int min, int max) {
		return Math.max(min, Math.min(max, val));
	}

	static float clamp(float val, float min, float max) {
		return Math.max(min, Math.min(max, val));
	}

	static long get(String val, long old, long def) {
		if(val == null) {
			return def;
		}

		try {
			return Long.parseUnsignedLong(val);
		} catch(NumberFormatException e) {
			return old;
		}
	}

	static long get(String val, long old, long def, long min, long max) {
		return clamp(get(val, old, def), min, max);
	}

	static int get(String val, int old, int def) {
		if(val == null) {
			return def;
		}

		try {
			return Integer.parseUnsignedInt(val);
		} catch(NumberFormatException e) {
			return old;
		}
	}

	static int get(String val, int old, int def, int min, int max) {
		return clamp(get(val, old, def), min, max);
	}
}
