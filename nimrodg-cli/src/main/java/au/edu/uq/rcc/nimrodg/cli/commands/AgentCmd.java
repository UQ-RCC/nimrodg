package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class AgentCmd extends NimrodCLICommand {
	private static final Map<String, Subcommand> COMMAND_MAP = Map.of(
			"list", AgentCmd::executeList
	);

	private AgentCmd() {

	}

	@Override
	public String getCommand() {
		return "agent";
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		return COMMAND_MAP.get(args.getString("operation")).main(nimrod, args, out, err, configDirs);
	}

	private static String toStringEmptyNull(Object o) {
		if(o == null) {
			return "";
		}

		return Objects.toString(o);
	}

	private static int executeList(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		String resName = args.getString("resource_name");

		Resource res = nimrod.getResource(resName);
		if(res == null) {
			err.printf("No such resource \"%s\"\n", resName);
			return 1;
		}

		SimpleTable st = SimpleTable.of().nextRow()
				.nextCell("UUID")
				.nextCell("Queue")
				.nextCell("State")
				.nextCell("Shutdown Signal")
				.nextCell("Shutdown Reason")
				.nextCell("Creation Time")
				.nextCell("Connection Time")
				.nextCell("Last Heard From")
				.nextCell("Expiry Time")
				.nextCell("Expired")
				.nextCell("Secret Key")
				.nextCell("Actuator Data");

		for(AgentInfo ai : nimrod.getResourceAgents(res)) {
			st.nextRow()
					.nextCell(toStringEmptyNull(ai.getUUID()))
					.nextCell(toStringEmptyNull(ai.getQueue()))
					.nextCell(toStringEmptyNull(ai.getState()))
					.nextCell(toStringEmptyNull(ai.getShutdownSignal()))
					.nextCell(toStringEmptyNull(ai.getShutdownReason()))
					.nextCell(toStringEmptyNull(ai.getCreationTime()))
					.nextCell(toStringEmptyNull(ai.getConnectionTime()))
					.nextCell(toStringEmptyNull(ai.getLastHeardFrom()))
					.nextCell(toStringEmptyNull(ai.getExpiryTime()))
					.nextCell(toStringEmptyNull(ai.getExpired()))
					.nextCell(toStringEmptyNull(ai.getSecretKey()))
					.nextCell(toStringEmptyNull(ai.getActuatorData()));
		}

		printTable(st, out);
		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new AgentCmd(), "Agent Operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers().dest("operation");

			Subparser list = subs.addParser("list")
					.help("List all the current agents.");
			addResNameArg(list);
		}
	};
}
