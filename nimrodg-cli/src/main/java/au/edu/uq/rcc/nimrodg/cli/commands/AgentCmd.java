package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.MachinePair;
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
import java.util.Map;
import java.util.Objects;

public final class AgentCmd extends NimrodCLICommand {
	private static final Map<String, Subcommand> COMMAND_MAP = Map.of(
			"list", AgentCmd::executeList,
			"platform", AgentCmd::executePlatform
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

	private static int executePlatform(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		SimpleTable st = SimpleTable.of().nextRow().nextCell("Platform").nextCell("Path");
		String op = args.getString("platop");

		switch(op) {
			case "list":
				nimrod.lookupAgents().forEach((k, v) -> st.nextRow().nextCell(k).nextCell(Objects.toString(v.getPath())));
				printTable(st, out);
				return 0;

			case "add": {
				String plat = args.getString("platform_string");
				String path = args.getString("path");
				nimrod.addAgentPlatform(plat, Path.of(path));
				st.nextRow().nextCell(plat).nextCell(path);
				printTable(st, out);
				return 0;
			}
			case "remove": {
				nimrod.deleteAgentPlatform(args.getString("platform_string"));
				return 0;
			}
			case "unmap": {
				nimrod.unmapAgentPosixPlatform(MachinePair.of(args.getString("system"), args.getString("machine")));
				return 0;
			}
			case "map": {
				String plat = args.getString("platform_string");
				MachinePair mp = MachinePair.of(args.getString("system"), args.getString("machine"));
				AgentDefinition platDef = nimrod.lookupAgentByPlatform(plat);
				AgentDefinition posixDef = nimrod.lookupAgentByPosix(mp);

				if(platDef == null) {
					err.printf("No such agent platform \"%s\"\n", plat);
					return 1;
				}

				if(posixDef != null) {
					if(plat.equals(posixDef.getPlatformString())) {
						return 0;
					}

					err.printf("Cannot map agent platform %s to (%s): Already mapped to %s.\n", plat, mp, posixDef.getPlatformString());
					return 1;
				}

				nimrod.mapAgentPosixPlatform(plat, mp);
				return 0;
			}
			default:
				break;
		}

		throw new IllegalArgumentException();
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new AgentCmd(), "Agent Operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers().dest("operation");

			Subparser list = subs.addParser("list")
					.help("List all the current agents.");
			addResNameArg(list);

			Subparser platform = subs.addParser("platform");

			Subparsers psub = platform.addSubparsers().dest("platop");

			psub.addParser("list")
					.help("List agent platforms")
					.description("List agent platforms");

			Subparser platAdd = psub.addParser("add")
					.help("Add an agent platform")
					.description("Add an agent platform");
			platAdd.addArgument("platform_string")
					.help("Platform string");
			platAdd.addArgument("path")
					.help("Path to agent binary");

			Subparser platRemove = psub.addParser("remove")
					.help("Remove an agent platform")
					.description("Remove an agent platform");
			platRemove.addArgument("platform_string")
					.help("Platform string");

			Subparser platMap = psub.addParser("map")
					.help("Map an agent platform to a POSIX (system, machine) pair")
					.description("Map an agent platform to a POSIX (system, machine) pair");
			platMap.addArgument("platform_string")
					.help("Platform string");
			platMap.addArgument("system")
					.help("POSIX system (uname -s)");
			platMap.addArgument("machine")
					.help("POSIX machine (uname -m)");

			Subparser platUnmap = psub.addParser("unmap")
					.help("Unmap an agent platform to a POSIX (system, machine) pair")
					.description("Unmap an agent platform to a POSIX (system, machine) pair");
			platUnmap.addArgument("platform_string")
					.help("Platform string");
			platUnmap.addArgument("system")
					.help("POSIX system (uname -s)");
			platUnmap.addArgument("machine")
					.help("POSIX machine (uname -m)");
		}
	};
}
