package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.DefaultCLICommand;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.impl.base.db.MigrationPlan;
import au.edu.uq.rcc.nimrodg.impl.base.db.NimrodAPIDatabaseFactory;
import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

public final class DbMigrateCmd extends DefaultCLICommand {

	private enum MigrateOperation {
		DUMP_SQL,
		PLAN_ONLY,
		MIGRATE_ASK,
		MIGRATE_AUTO,
	}

	private DbMigrateCmd() {

	}

	@Override
	public int execute(Namespace args, UserConfig config, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		NimrodAPIFactory _fact = NimrodCLICommand.createFactory(config);
		if(!(_fact instanceof NimrodAPIDatabaseFactory)) {
			err.println("Not a database-backed Nimrod implementation, cannot continue.");
			return 1;
		}
		NimrodAPIDatabaseFactory fact = (NimrodAPIDatabaseFactory)_fact;

		switch(args.getString("migop")) {
			case "apply":
			case "plan":
				return planOrApply(args, config, out, err, configDirs);

			case "reset": {
				int r;

				MigrationPlan plan = fact.buildResetPlan();
				try(Connection c = fact.createConnection(config)) {
					c.setAutoCommit(false);
					r = executeMigrationPlan(c, plan, MigrateOperation.MIGRATE_AUTO, out, err);
					c.commit();
				}
				return r;
			}

			case "show-paths":
				return DbMigrateCmd.showPaths(fact, out);
		}

		return 0;
	}

	private static int showPaths(NimrodAPIDatabaseFactory factory, PrintStream out) {
		SimpleTable st = SimpleTable.of().nextRow().nextCell("From").nextCell("To");
		factory.getUpgradePairs().forEach(up -> st.nextRow()
				.nextCell(Objects.toString(up.from)).nextCell(Objects.toString(up.to))
		);

		printTable(st, out);
		return 0;
	}

	private static int executeMigrationPlan(Connection c, MigrationPlan plan, MigrateOperation mop, PrintStream out, PrintStream err) throws SQLException {
		if(!plan.valid) {
			err.println("Migration plan failure!");
			err.printf("  From:    %s\n", plan.from);
			err.printf("  To:      %s\n", plan.to);
			err.printf("  Message: %s\n", plan.message);
			return 1;
		}

		if(MigrateOperation.DUMP_SQL.equals(mop)) {
			out.printf("%s\n", plan.sql);
			return 0;
		}

		out.printf("Migration plan completed, %d steps\n", plan.path.size());
		plan.path.forEach(s -> out.printf("  %s -> %s\n", s.from, s.to));

		/* If we're just planning, bail here. */
		if(MigrateOperation.PLAN_ONLY.equals(mop)) {
			return 0;
		}

		if(plan.path.isEmpty()) {
			out.println("Nothing to do...");
			return 0;
		}

		if(MigrateOperation.MIGRATE_ASK.equals(mop)) {
			out.println("Ready to run migration, does the above plan look correct?");
			out.println("Please type \"yes\" (without quotes). Anything else will abort the process.");
			out.print("> ");
			out.flush();

			try(Scanner s = new Scanner(System.in)) {
				String yes = s.nextLine();
				if(!"yes".equals(yes.strip())) {
					out.println("Incorrect response entered, migration aborted.");
					return 1;
				}
			}

			mop = MigrateOperation.MIGRATE_AUTO;
		}

		assert MigrateOperation.MIGRATE_AUTO.equals(mop);

		/* In for a penny, in for a pound. */
		c.setAutoCommit(false);
		try(Statement s = c.createStatement()) {
			s.executeUpdate(plan.sql);
		}
		c.commit();

		return 0;
	}

	private static int planOrApply(Namespace args, UserConfig config, PrintStream out, PrintStream err, Path[] configDirs) throws SQLException, ReflectiveOperationException {
		NimrodAPIDatabaseFactory fact = (NimrodAPIDatabaseFactory)NimrodCLICommand.createFactory(config);

		SchemaVersion from = null, to = null;
		try(Connection c = fact.createConnection(config)) {
			String _from = args.getString("from_version");
			if(_from != null) {
				from = SchemaVersion.parse(_from);
			}

			String _to = args.getString("to_version");
			if(_to != null) {
				to = SchemaVersion.parse(_to);
			}

			MigrationPlan plan;
			if(from == null && to == null) {
				/* If no from/to specified, upgrade if necessary. */
				plan = fact.buildUpgradePlan(c);
			} else {
				if(from == null) {
					from = fact.getCurrentSchemaVersion(c);
				}

				if(to == null) {
					to = fact.getNativeSchemaVersion();
				}

				plan = fact.buildMigrationPlan(from, to);
			}

			MigrateOperation mop;
			if(Optional.ofNullable(args.getBoolean("dump_sql")).orElse(false)) {
				mop = MigrateOperation.DUMP_SQL;
			} else if("plan".equals(args.getString("migop"))) {
				mop = MigrateOperation.PLAN_ONLY;
			} else if(Optional.ofNullable(args.getBoolean("auto_approve")).orElse(false)) {
				mop = MigrateOperation.MIGRATE_AUTO;
			} else {
				mop = MigrateOperation.MIGRATE_ASK;
			}

			return executeMigrationPlan(c, plan, mop, out, err);
		}
	}

	@Override
	public String getCommand() {
		return "migrate";
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new DbMigrateCmd(), "Database migration functionality.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers sp = parser.addSubparsers().dest("migop");

			Subparser migPlan = sp.addParser("plan")
					.help("Plan a migration")
					.description("Plan a migration");

			migPlan.addArgument("--dump-sql")
					.action(Arguments.storeTrue())
					.setDefault(false)
					.help("Dump the SQL to standard output");

			migPlan.addArgument("-f", "--from").dest("from_version")
					.metavar("version")
					.help("Starting schema version. Defaults to current.");

			migPlan.addArgument("-t", "--to").dest("to_version")
					.metavar("version")
					.help("Target schema version. Defaults to the latest.");

			Subparser migApply = sp.addParser("apply")
					.help("Apply a migration")
					.description("Apply a migration");

			migApply.addArgument("--auto-approve")
					.action(Arguments.storeTrue())
					.setDefault(false)
					.help("Don't ask for approval");

			migApply.addArgument("-f", "--from").dest("from_version")
					.metavar("version")
					.help("Starting schema version. Defaults to current.");

			migApply.addArgument("-t", "--to").dest("to_version")
					.metavar("version")
					.help("Target schema version. Defaults to the latest.");


			sp.addParser("reset")
					.help("Reset to default, empty state. THIS WILL NOT ASK FOR CONFIRMATION.");

			sp.addParser("show-paths")
					.help("Show migration paths")
					.description("Show migration paths");
		}
	};
}
