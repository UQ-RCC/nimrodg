package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
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

public final class PropertyCmd extends NimrodCLICommand {
	private static final Map<String, Subcommand> COMMAND_MAP = Map.of(
			"list", PropertyCmd::executeList,
			"get", PropertyCmd::executeGet,
			"set", PropertyCmd::executeSet,
			"delete", PropertyCmd::executeDelete
	);

	private PropertyCmd() {

	}

	@Override
	public String getCommand() {
		return "property";
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		return COMMAND_MAP.get(args.getString("operation")).main(nimrod, args, out, err, configDirs);
	}

	private static int executeList(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		SimpleTable st = SimpleTable.of().nextRow().nextCell("Key").nextCell("Value");
		nimrod.getProperties().forEach((k, v) -> st.nextRow().nextCell(k).nextCell(v));
		printTable(st, out);
		return 0;
	}

	private static int executeGet(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		nimrod.getProperty(args.getString("key")).ifPresent(out::println);
		return 0;
	}

	private static int executeSet(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		nimrod.setProperty(args.getString("key"), args.getString("value"));
		return 0;
	}

	private static int executeDelete(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		nimrod.setProperty(args.getString("key"), "");
		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new PropertyCmd(), "Property Operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers().dest("operation");

			subs.addParser("list")
					.help("List all the current properties.");

			Subparser get = subs.addParser("get")
					.help("Get the value of the specified property");
			get.addArgument("key")
					.type(String.class)
					.required(true);

			Subparser set = subs.addParser("set")
					.help("Set the value of the specified property");
			set.addArgument("key")
					.type(String.class)
					.required(true);
			set.addArgument("value")
					.type(String.class)
					.required(true);

			Subparser del = subs.addParser("delete")
					.help("Delete a property.");
			del.addArgument("key")
					.type(String.class)
					.required(true);
		}
	};
}
