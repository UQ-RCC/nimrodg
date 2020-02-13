package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.*;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ExperimentCmd extends NimrodCLICommand {
    @Override
    public String getCommand() {
        return "experiment";
    }

    @Override
    public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
        switch(args.getString("operation")) {
            case "add":
                return executeAdd(args, config, nimrod, out, err, configDirs);
            case "delete":
                return executeDelete(args, config, nimrod, out, err, configDirs);
        }
        return 0;
    }

    private int executeAdd(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
        String expName = args.getString("exp_name");
        String runFile = args.getString("planfile");

        NimrodParseAPI parseApi = ANTLR4ParseAPIImpl.INSTANCE;
        /* Load the runfile */
        RunBuilder b;
        try {
            if(runFile.equals("-")) {
                b = parseApi.parseRunToBuilder(System.in);
            } else {
                b = parseApi.parseRunToBuilder(Paths.get(runFile));
            }
        } catch(PlanfileParseException ex) {
            ex.getErrors().forEach(e -> System.err.println(e.toString(runFile)));
            return 1;
        }

        CompiledRun rf;
        try {
            rf = b.build();
        } catch(RunBuilder.RunfileBuildException e) {
            throw new NimrodException(e);
        }

        /* Now add it */
        Experiment exp = nimrod.getExperiment(expName);
        if(exp != null) {
            err.printf("Duplicate experiment '%s'.\n", expName);
            return 1;
        }

        /* FIXME: I feel that this shouldn't be here. */
        Path rootStore = Paths.get(nimrod.getConfig().getRootStore());
        Path workDir = rootStore.resolve(expName);

        try {
            if(Files.exists(workDir)) {
                NimrodUtils.deltree(workDir);
            }

            Files.createDirectories(workDir);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }

        nimrod.addExperiment(expName, rf);

        return 0;
    }

    private int executeDelete(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
        String expName = args.getString("exp_name");

        Experiment exp = nimrod.getExperiment(expName);
        if(exp == null) {
            return 0;
        }

        /* FIXME: This shouldn't be here. */
        Path workDir = Paths.get(nimrod.getConfig().getRootStore()).resolve(exp.getWorkingDirectory());
        NimrodUtils.deltree(workDir);

        nimrod.deleteExperiment(exp);
        return 0;
    }

    public static final CommandEntry DEFINITION = new CommandEntry(new ExperimentCmd(), "Experiment Operations.") {
        @Override
        public void addArgs(Subparser parser) {
            super.addArgs(parser);

            Subparsers subs = parser.addSubparsers()
                    .dest("operation");
            {
                Subparser sp = subs.addParser("add")
                        .help("Add an experiment.")
                        .description("Add a new, empty experiment from a planfile.");

                addExpNameArg(sp);
                sp.addArgument("planfile")
                        .metavar("planfile.pln")
                        .type(String.class)
                        .help("The planfile path. Omit or use '-' to read from stdin.")
                        .setDefault("-")
                        .nargs("?");
            }

            {
                Subparser sp = subs.addParser("delete")
                        .help("Delete an experiment.")
                        .description("Delete an experiment and all associated data from the database.");

                addExpNameArg(sp);
            }
        }
    };
}
