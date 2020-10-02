package au.edu.uq.rcc.nimrodg.api;

public class JobSpecifications {
	/**
	 * The number of CPUs required for a task. 0 if unknown.
	 */
	public final long ncpus;

	/**
	 * The amount of memory in bytes required for a task. 0 if unknown.
	 */
	public final long memory;

	/**
	 * The walltime in seconds required for a task. 0 if unknown.
	 * This is mostly useful for HPC actuators.
	 */
	public final long walltime;

	/**
	 * The amount of scratch space in bytes required for a task. 0 if unknown.
	 */
	public final long scratch;

	public JobSpecifications(long ncpus, long memory, long walltime, long scratch) {
		if((this.ncpus = ncpus) < 0) {
			throw new IllegalArgumentException("ncpus cannot be < 0");
		}

		if((this.memory = memory) < 0) {
			throw new IllegalArgumentException("memory cannot be < 0");
		}

		if((this.walltime = walltime) < 0) {
			throw new IllegalArgumentException("walltime cannot be < 0");
		}

		if((this.scratch = scratch) < 0) {
			throw new IllegalArgumentException("scratch cannot be < 0");
		}
	}

	public static JobSpecifications empty() {
		return new JobSpecifications(0L, 0L, 0L, 0L);
	}
}