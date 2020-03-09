package au.edu.uq.rcc.nimrodg.api.utils.run;

import au.edu.uq.rcc.nimrodg.api.Task;

public class RunfileBuildException extends Exception {
	RunfileBuildException(String s) {
		super(s);
	}

	public static class FirstVariableIndexNonZero extends RunfileBuildException {
		FirstVariableIndexNonZero(CompiledVariable var) {
			super(String.format("First variable '%s' has nonzero index", var.name));
		}
	}

	public static class DuplicateVariable extends RunfileBuildException {
		DuplicateVariable(String name, int count) {
			super(String.format("Duplicate variable '%s', %d instances", name, count));
		}
	}

	public static class DuplicateVariableIndex extends RunfileBuildException {
		DuplicateVariableIndex(CompiledVariable v1, CompiledVariable v2) {
			super(String.format("Variables '%s' and '%s' share index %d", v1.name, v2.name, v1.index));
		}
	}

	public static class NonConsecutiveVariableIndex extends RunfileBuildException {
		NonConsecutiveVariableIndex(CompiledVariable v1, CompiledVariable v2) {
			super(String.format("Variable '%s' (%d) has partner '%s' (%d) with non-consecutive index", v1.name, v1.index, v2.name, v2.index));
		}
	}

	public static class FirstJobIndexNonOne extends RunfileBuildException {
		FirstJobIndexNonOne(CompiledJob job) {
			super(String.format("First job has non-one index '%d'", job.index));
		}
	}

	public static class DuplicateJobIndex extends RunfileBuildException {
		DuplicateJobIndex(int index) {
			super(String.format("Multiple jobs with same index %d", index));
		}
	}

	public static class NonConsecutiveJobIndex extends RunfileBuildException {
		NonConsecutiveJobIndex(int i1, int i2) {
			super(String.format("Job indices '%d' and '%d' are nonconsecutive", i1, i2));
		}
	}

	public static class InvalidJobVariables extends RunfileBuildException {
		InvalidJobVariables(CompiledJob j) {
			super(String.format("Job '%d' has missing or too many variable references", j.index));
		}
	}

	public static class InvalidJobVariableIndex extends RunfileBuildException {
		InvalidJobVariableIndex(CompiledJob j, CompiledVariable var, int index) {
			super(String.format("Job '%d' references invalid value index '%d' in variable '%s'", j.index, index, var.name));
		}
	}

	public static class DuplicateTaskName extends RunfileBuildException {
		DuplicateTaskName(Task.Name name) {
			super(String.format("Duplicate task name %s", name.toString().toLowerCase()));
		}
	}

	public static class MissingMainTask extends RunfileBuildException {
		MissingMainTask() {
			super("Missing main task");
		}
	}

	public static class NonMainHasSubstitutions extends RunfileBuildException {
		NonMainHasSubstitutions() {
			super("Non main task is not allowed to have substitutions");
		}
	}

	public static class InvalidVariableSubstitutionReference extends RunfileBuildException {
		InvalidVariableSubstitutionReference(String var) {
			super(String.format("Invalid variable reference '%s' in substitution", var));
		}
	}

	public static class ImplicitVariableConflict extends RunfileBuildException {
		ImplicitVariableConflict() {
			super("Variable name conflicts with an implicit variable");
		}
	}
}
