package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import java.io.PrintStream;
import java.nio.file.Path;

public final class ResourceTypeCmd extends NimrodCLICommand {
	@Override
	public String getCommand() {
		return "resource-type";
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		String name = args.getString("name");

		SimpleTable st = SimpleTable.of().nextRow().nextCell("Name").nextCell("Class");

		switch(args.getString("operation")) {
			case "add": {
				ResourceTypeInfo rt = nimrod.addResourceType(name, args.getString("class"));
				st.nextRow().nextCell(rt.type).nextCell(rt.className);
				printTable(st, out);
				return 0;
			}
			case "remove": {
				nimrod.deleteResourceType(name);
				return 0;
			}
			case "list": {
				for(ResourceTypeInfo rt : nimrod.getResourceTypeInfo()) {
					st.nextRow().nextCell(rt.type).nextCell(rt.className);
				}

				printTable(st, out);
				return 0;
			}
			default:
				return 1;
		}
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new ResourceTypeCmd(), "Resource type operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers()
					.dest("operation");

			subs.addParser("list")
					.help("List resource types")
					.description("List resource types");

			Subparser add = subs.addParser("add")
					.help("Add a resource type.")
					.description("Add a resource type.");

			add.addArgument("name")
					.help("Resource type name")
					.required(true);

			add.addArgument("class")
					.help("Resource type factory class")
					.required(true);


			Subparser remove = subs.addParser("remove")
					.help("Remove a resource type.")
					.description("Remove a resource type.");

			remove.addArgument("name")
					.help("Resource type name")
					.required(true);

		}
	};
}
