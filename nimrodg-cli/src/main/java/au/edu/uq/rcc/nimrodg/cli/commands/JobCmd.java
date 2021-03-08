package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodException;
import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class JobCmd extends NimrodCLICommand {

	private static final Map<String, Subcommand> COMMAND_MAP = Map.of(
			"list", JobCmd::executeList,
			"add", JobCmd::executeAdd
	);

	@Override
	public String getCommand() {
		return "job";
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		return COMMAND_MAP.get(args.getString("operation")).main(nimrod, args, out, err, configDirs);
	}

	private static SimpleTable buildJobTable(NimrodAPI nimrod, Collection<Job> jobs) {
		SimpleTable st = SimpleTable.of()
				.nextRow()
				.nextCell("Index")
				.nextCell("Creation Time")
				.nextCell("Status")
				.nextCell("Cached Status")
				.nextCell("Variables");

		JsonObjectBuilder jb = Json.createObjectBuilder();

		Collection<JobAttempt.Status> stats = nimrod.getJobStatuses(jobs);
		Iterator<JobAttempt.Status> sit = stats.iterator();
		for(Job j : jobs) {
			j.getVariables().forEach(jb::add);
			st.nextRow()
					.nextCell(Long.toString(j.getIndex()))
					.nextCell(Objects.toString(j.getCreationTime()))
					.nextCell(Objects.toString(sit.next()))
					.nextCell(Objects.toString(j.getCachedStatus()))
					.nextCell(jb.build().toString());
		}
		return st;
	}

	private static int executeList(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		String expName = args.getString("exp_name");
		Experiment exp = nimrod.getExperiment(expName);

		if(exp == null) {
			err.printf("No such experiment \"%s\"\n", expName);
			return 1;
		}

		EnumSet<JobAttempt.Status> statuses;
		List<JobAttempt.Status> _statuses = args.getList("status");
		if(_statuses == null) {
			statuses = EnumSet.allOf(JobAttempt.Status.class);
		} else {
			statuses = EnumSet.copyOf(_statuses);
		}

		Collection<Job> jobs = nimrod.filterJobs(
				exp,
				statuses,
				args.getLong("start"),
				args.getInt("limit")
		);

		printTable(buildJobTable(nimrod, jobs), out);
		return 0;
	}

	private static JsonArray readJsonArray(InputStream is) {
		try(JsonReader r = Json.createReader(is)) {
			return r.readArray();
		}
	}

	private static int executeAdd(NimrodAPI nimrod, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {
		String expName = args.getString("exp_name");
		String jobsFile = args.getString("jobsfile");

		/* Make sure the experiment exists first */
		Experiment exp = nimrod.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'.\n", expName);
			return 1;
		}

		/* Load the jobsfile */
		JsonArray ja;
		if(jobsFile.equals("-")) {
			ja = readJsonArray(System.in);
		} else {
			try(InputStream is = Files.newInputStream(Paths.get(jobsFile))) {
				ja = readJsonArray(is);
			}
		}

		List<Map<String, String>> jobs = JsonUtils.jobsFileFromJson(ja);

		/* Do some quick validation before hitting the db. */
		Set<Set<String>> sss = jobs.stream()
				.map(Map::keySet)
				.collect(Collectors.toSet());
		if(sss.size() != 1) {
			err.println("Mismatched variable names.");
			return 1;
		}

		/* Now add it */
		if(!sss.stream().findFirst().get().equals(exp.getVariables())) {
			err.println("Job variables don't match run variables.");
			return 1;
		}

		Collection<Job> newJobs = nimrod.addJobs(exp, jobs);
		printTable(buildJobTable(nimrod, newJobs), out);
		return 0;
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new JobCmd(), "Job operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);

			Subparsers subs = parser.addSubparsers().dest("operation");

			Subparser sp = subs.addParser("list")
					.help("List the jobs of an experiment.");
			addExpNameArg(sp);

			sp.addArgument("-s", "--start")
					.help("Start at this job index.")
					.type(Long.class)
					.setDefault(0L);

			sp.addArgument("-l", "--limit")
					.help("Limit the listing to this many jobs.")
					.type(Integer.class)
					.setDefault(10);

			sp.addArgument("--status")
					.help("Only list jobs with these statuses.")
					.nargs("*")
					.type(JobAttempt.Status.class)
					.choices(EnumSet.allOf(JobAttempt.Status.class));

			Subparser add = subs.addParser("add")
					.help("Add jobs from a file.");
			addExpNameArg(add);
			add.addArgument("jobsfile")
					.type(String.class)
					.help("The jobsfile path. Omit or use '-' to read from stdin.")
					.setDefault("-")
					.nargs("?");
		}
	};
}
