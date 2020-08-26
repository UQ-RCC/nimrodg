package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.resource.TestShell;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.hubspot.jinjava.Jinjava;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandDefinitionTests {

	public static final String[] JOB_IDS_PBSPRO = new String[]{"1.tinmgr2", "2.tinmgr2", "3.tinmgr2"};

	public static final JsonObject PAYLOAD_PBSPRO = Json.createObjectBuilder()
			.add("timestamp", 1597727453)
			.add("pbs_version", "18.1.2")
			.add("pbs_server", "tinmgr2")
			.add("Jobs", Json.createObjectBuilder()
					.add("1.tinmgr2", Json.createObjectBuilder()
							.add("ctime", "Tue Aug 18 15:10:12 2020")
							.add("job_state", "Q")
					).add("2.tinmgr2", Json.createObjectBuilder()
							.add("ctime", "Tue Aug 18 15:10:12 2020")
							.add("job_state", "R")
					).add("3.tinmgr2", Json.createObjectBuilder()
							.add("ctime", "Tue Aug 18 15:10:12 2020")
							.add("job_state", "E")
					)).build();

	public static final Map<String, String> ATTRIBUTES_PBSPRO = Map.of(
			"state", "/Jobs/{{ jobid }}/job_state",
			"pbsinfo", "/Jobs/{{ jobid }}"
	);

	public static final String[] JOB_IDS_SLURM = new String[]{"281311", "281386", "281482"};

	public static final String PAYLOAD_SLURM = "281311              PENDING             \n" +
			"281386              PENDING             \n" +
			"281482              RUNNING             \n";


	private static final SubmitCommand.Response EXPECTED_RESPONSE_QSUB = new SubmitCommand.Response(
			new RemoteShell.CommandResult("qsub /path/to/script.sh", 0, "1.tinmgr2", ""),
			"1.tinmgr2"
	);

	private static final QueryCommand.Response EXPECTED_RESPONSE_QSTAT = new QueryCommand.Response(
			new RemoteShell.CommandResult("qstat -F json -f -x 1.tinmgr2 2.tinmgr2 3.tinmgr2", 0, PAYLOAD_PBSPRO.toString(), ""),
			Map.of(
					"1.tinmgr2", new QueryCommand.JobQueryInfo(
							"1.tinmgr2",
							Actuator.AgentStatus.Launching,
							Json.createObjectBuilder()
									.add("state", Json.createPointer("/Jobs/1.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
									.add("pbsinfo", Json.createPointer("/Jobs/1.tinmgr2").getValue(PAYLOAD_PBSPRO))
									.build()
					), "2.tinmgr2", new QueryCommand.JobQueryInfo(
							"2.tinmgr2",
							Actuator.AgentStatus.Launched,
							Json.createObjectBuilder()
									.add("state", Json.createPointer("/Jobs/2.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
									.add("pbsinfo", Json.createPointer("/Jobs/2.tinmgr2").getValue(PAYLOAD_PBSPRO))
									.build()
					), "3.tinmgr2", new QueryCommand.JobQueryInfo(
							"3.tinmgr2",
							Actuator.AgentStatus.Disconnected,
							Json.createObjectBuilder()
									.add("state", Json.createPointer("/Jobs/3.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
									.add("pbsinfo", Json.createPointer("/Jobs/3.tinmgr2").getValue(PAYLOAD_PBSPRO))
									.build()
					)
			)
	);

	private static final QueryCommand.Response EXPECTED_RESPONSE_SQUEUE = new QueryCommand.Response(
			new RemoteShell.CommandResult("squeue --noheader --Format=jobid,state --job 281311,281386,281482", 0, PAYLOAD_SLURM, ""),
			Map.of(
					"281311", new QueryCommand.JobQueryInfo(
							"281311",
							Actuator.AgentStatus.Unknown,
							Json.createObjectBuilder()
									.add("jobid", "281311")
									.add("state", "PENDING")
									.build()
					), "281386", new QueryCommand.JobQueryInfo(
							"281386",
							Actuator.AgentStatus.Unknown,
							Json.createObjectBuilder()
									.add("jobid", "281386")
									.add("state", "PENDING")
									.build()
					), "281482", new QueryCommand.JobQueryInfo(
							"281482",
							Actuator.AgentStatus.Unknown,
							Json.createObjectBuilder()
									.add("jobid", "281482")
									.add("state", "RUNNING")
									.build()
					)
			)
	);

	private Path fsRoot;

	@Before
	public void before() {
		fsRoot = Jimfs.newFileSystem(Configuration.unix().toBuilder().setAttributeViews("posix").build()).getPath("/");
	}

	@Test
	public void hpcJsonValidationTest() throws IOException {
		List<String> errors = new ArrayList<>();
		try {
			HPCResourceType.loadConfig(new Path[0], errors);
		} finally {
			errors.forEach(System.err::println);
		}

		Assert.assertEquals(List.of(), errors);
	}

	@Test
	public void jsonParseTest() {
		JsonParser jp = new JsonParser("jobid", ATTRIBUTES_PBSPRO);

		JsonObject expected = Json.createObjectBuilder()
				.add("1.tinmgr2", Json.createObjectBuilder()
						.add("state", Json.createPointer("/Jobs/1.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
						.add("pbsinfo", Json.createPointer("/Jobs/1.tinmgr2").getValue(PAYLOAD_PBSPRO))
				)
				.add("2.tinmgr2", Json.createObjectBuilder()
						.add("state", Json.createPointer("/Jobs/2.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
						.add("pbsinfo", Json.createPointer("/Jobs/2.tinmgr2").getValue(PAYLOAD_PBSPRO))
				)
				.add("3.tinmgr2", Json.createObjectBuilder()
						.add("state", Json.createPointer("/Jobs/3.tinmgr2/job_state").getValue(PAYLOAD_PBSPRO))
						.add("pbsinfo", Json.createPointer("/Jobs/3.tinmgr2").getValue(PAYLOAD_PBSPRO))
				)
				.build();

		Assert.assertEquals(expected, jp.parse(PAYLOAD_PBSPRO.toString(), JOB_IDS_PBSPRO));
	}

	@Test
	public void jsonParseTestDummy() {
		JsonParser p = new JsonParser("dummy", Map.of("jobid", "/name"));

		String payload = Json.createObjectBuilder()
				.add("name", "1.tinmgr2")
				.build().toString();

		JsonObject expected = Json.createObjectBuilder()
				.add("dummy", Json.createObjectBuilder().add("jobid", "1.tinmgr2"))
				.build();

		Assert.assertEquals(expected, p.parse(payload, new String[]{"dummy"}));
	}

	@Test
	public void lineRegexParseTest() {
		LineRegexParser p = new LineRegexParser(Pattern.compile("^(?<jobid>\\d+)\\s+(?<state>\\w+)\\s*$"), "jobid", 3);

		JsonObject expected = Json.createObjectBuilder()
				.add("281311", Json.createObjectBuilder()
						.add("jobid", "281311")
						.add("state", "PENDING"))
				.add("281386", Json.createObjectBuilder()
						.add("jobid", "281386")
						.add("state", "PENDING"))
				.add("281482", Json.createObjectBuilder()
						.add("jobid", "281482")
						.add("state", "RUNNING"))
				.build();

		Assert.assertEquals(expected, p.parse(PAYLOAD_SLURM, JOB_IDS_SLURM));
	}

	@Test
	public void commandParsingTest() throws IOException {
		SubmitCommand qsub = new SubmitCommand.Builder()
				.argv("qsub", "{{ script_path }}")
				.parser(new LineRegexParser(Pattern.compile("^(?<jobid>.+)$"), "jobid", 1))
				.build();

		QueryCommand qstat = new QueryCommand.Builder()
				.argv("qstat", "-F", "json", "-f", "-x")
				.appendJobIds(true)
				.parser(new JsonParser("jobid", Map.of(
						"state", "/Jobs/{{ jobid }}/job_state",
						"pbsinfo", "/Jobs/{{ jobid }}"
				)))
				.mapState("Q", Actuator.AgentStatus.Launching)
				.mapState("R", Actuator.AgentStatus.Launched)
				.mapState("E", Actuator.AgentStatus.Disconnected)
				.build();

		QueryCommand squeue = new QueryCommand.Builder()
				.argv("squeue", "--noheader", "--Format=jobid,state", "--job", "{{ jobids|join(',') }}")
				.appendJobIds(false)
				.parser(new LineRegexParser(Pattern.compile("^(?<jobid>\\d+)\\s+(?<state>\\w+)\\s*$"), "jobid", 0))
				.build();

		try(TestShell sh = new TestShell(fsRoot)) {
			sh.addCommandProcessor("qsub", (argv, input) -> new RemoteShell.CommandResult(
					argv,
					EXPECTED_RESPONSE_QSUB.commandResult.status,
					EXPECTED_RESPONSE_QSUB.commandResult.stdout,
					EXPECTED_RESPONSE_QSUB.commandResult.stderr
			));
			sh.addCommandProcessor("qstat", (argv, input) -> new RemoteShell.CommandResult(
					argv,
					EXPECTED_RESPONSE_QSTAT.commandResult.status,
					EXPECTED_RESPONSE_QSTAT.commandResult.stdout,
					EXPECTED_RESPONSE_QSTAT.commandResult.stderr
			));
			sh.addCommandProcessor("squeue", (argv, input) -> new RemoteShell.CommandResult(
					argv,
					EXPECTED_RESPONSE_SQUEUE.commandResult.status,
					EXPECTED_RESPONSE_SQUEUE.commandResult.stdout,
					EXPECTED_RESPONSE_SQUEUE.commandResult.stderr
			));

			SubmitCommand.Response qsubR = qsub.execute(sh, "/path/to/script.sh");
			Assert.assertEquals(EXPECTED_RESPONSE_QSUB, qsubR);

			QueryCommand.Response qstatR = qstat.execute(sh, JOB_IDS_PBSPRO);
			Assert.assertEquals(EXPECTED_RESPONSE_QSTAT, qstatR);

			QueryCommand.Response squeueR = squeue.execute(sh, JOB_IDS_SLURM);
			Assert.assertEquals(EXPECTED_RESPONSE_SQUEUE, squeueR);
		}
	}


	private static void testTemplate(HPCDefinition def, String marker, Set<String> expected) {
		String renderedTemplate = HPCActuator.renderTemplate(def.submitTemplate, HPCActuator.TEMPLATE_SAMPLE_VARS);
		Set<String> hash = Arrays.stream(renderedTemplate.split("\n"))
				.map(String::trim)
				.filter(l -> l.startsWith(marker))
				.collect(Collectors.toSet());

		Assert.assertEquals(expected, hash);
	}

	@Test
	public void hpcTemplateTests() throws IOException {
		Map<String, HPCDefinition> hpcDefs = HPCResourceType.loadConfig(new Path[0], new ArrayList<>());

		testTemplate(hpcDefs.get("pbspro"), "#PBS", Set.of(
				"#PBS -N nimrod-hpc-57ace1d4-0f8d-4439-9181-0fe91d6d73d4",
				"#PBS -l walltime=86400",
				"#PBS -l select=1:ncpus=120:mem=42949672960b",
				"#PBS -o /remote/path/to/stdout.txt",
				"#PBS -e /remote/path/to/stderr.txt",
				"#PBS -A account",
				"#PBS -q workq@tinmgr2.ib0"
		));

		testTemplate(hpcDefs.get("slurm"), "#SBATCH", Set.of(
				"#SBATCH --job-name nimrod-hpc-57ace1d4-0f8d-4439-9181-0fe91d6d73d4",
				"#SBATCH --time=1440",
				"#SBATCH --ntasks=10",
				"#SBATCH --cpus-per-task=12",
				"#SBATCH --mem-per-cpu=349526K",
				"#SBATCH --output /remote/path/to/stdout.txt",
				"#SBATCH --error /remote/path/to/stderr.txt",
				"#SBATCH --account account"
		));

		testTemplate(hpcDefs.get("lsf"), "#BSUB", Set.of(
				"#BSUB -J nimrod-hpc-57ace1d4-0f8d-4439-9181-0fe91d6d73d4",
				"#BSUB -W 1440",
				"#BSUB -n 120",
				"#BSUB -M 41943040KB",
				"#BSUB -o /remote/path/to/stdout.txt",
				"#BSUB -e /remote/path/to/stderr.txt"
		));
	}

}
