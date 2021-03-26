package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.impl.base.db.MigrationPlan;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodAPIDatabaseFactory;
import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;

public final class DbStatsCmd extends DefaultCLICommand {
	private DbStatsCmd() {
	}

	@Override
	public int execute(Namespace args, UserConfig cfg, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		NimrodAPIFactory _fact = NimrodCLICommand.createFactory(cfg);
		if(!(_fact instanceof NimrodAPIDatabaseFactory)) {
			err.println("Not a database-backed Nimrod implementation, cannot continue.");
			return 1;
		}
		NimrodAPIDatabaseFactory fact = (NimrodAPIDatabaseFactory)_fact;

		SimpleTable st = SimpleTable.of().nextRow()
				.nextCell("Key")
				.nextCell("Value");

		MigrationPlan upgradePlan;

		try(Connection c = fact.createConnection(cfg)) {
			st.nextRow()
					.nextCell("Current Schema Version")
					.nextCell(Objects.toString(fact.getCurrentSchemaVersion(c)));
		}

		st.nextRow()
				.nextCell("Native Schema Version")
				.nextCell(Objects.toString(fact.getNativeSchemaVersion()));

		st.nextRow()
				.nextCell("Factory Class")
				.nextCell(fact.getClass().getCanonicalName());

		printTable(st, out);
		return 0;
	}

	@Override
	public String getCommand() {
		return "stats";
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new DbStatsCmd(), "Display database statistics.");
}
