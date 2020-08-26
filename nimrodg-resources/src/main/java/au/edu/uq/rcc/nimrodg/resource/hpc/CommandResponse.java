package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import java.util.Objects;

public class CommandResponse {
	public final RemoteShell.CommandResult commandResult;

	protected CommandResponse(RemoteShell.CommandResult commandResult) {
		this.commandResult = Objects.requireNonNull(commandResult, "commandResult");
	}

	static CommandResponse empty(RemoteShell.CommandResult commandResult) {
		return new CommandResponse(commandResult);
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		CommandResponse that = (CommandResponse)o;
		return commandResult.equals(that.commandResult);
	}

	@Override
	public int hashCode() {
		return Objects.hash(commandResult);
	}
}
