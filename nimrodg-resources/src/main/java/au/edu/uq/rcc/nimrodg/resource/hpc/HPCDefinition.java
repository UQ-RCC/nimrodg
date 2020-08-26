package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

public class HPCDefinition {

	static JsonObject JSON_SCHEMA = ActuatorUtils.loadInternalSchema(HPCDefinition.class, "hpc_definition.json");

	public static HPCDefinition DEFINITION_PBSPRO = new HPCDefinition(
			"pbspro",
			new SubmitCommand.Builder()
					.argv("qsub", "{{ script_path }}")
					.parser(new LineRegexParser(Pattern.compile("^(?<jobid>.+)$"), "jobid", 1))
					.build(),
			new DeleteCommand.Builder()
					.argv("qdel")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new DeleteCommand.Builder()
					.argv("qdel", "-W", "force")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new QueryCommand.Builder()
					.argv("qstat", "-F", "json", "-f", "-x")
					.appendJobIds(true)
					.parser(new JsonParser("jobid", Map.of(
							"state", "/Jobs/{{ jobid }}/job_state",
							"pbsinfo", "/Jobs/{{ jobid }}"
					)))
					.mapState("B", "Launching")
					.mapState("E", "Disconnected")
					.mapState("F", "Disconnected")
					.mapState("H", "Launching")
					.mapState("M", "Launching")
					.mapState("Q", "Launching")
					.mapState("R", "Launched")
					.mapState("S", "Launched")
					.mapState("T", "Launching")
					.mapState("U", "Unknown")
					.mapState("W", "Launching")
					.mapState("X", "Disconnected")
					.build(),
			new String(ActuatorUtils.loadInternalFile(HPCDefinition.class, "hpc.pbspro.j2"), StandardCharsets.UTF_8)
	);

	public static final HPCDefinition DEFINITION_SLURM = new HPCDefinition(
			"slurm",
			new SubmitCommand.Builder()
					.argv("sbatch", "{{ script_path }}")
					.parser(new LineRegexParser(Pattern.compile("^.*?(?<jobid>\\d+).*$"), "jobid", 1))
					.build(),
			new DeleteCommand.Builder()
					.argv("scancel", "-b")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new DeleteCommand.Builder()
					.argv("scancel", "-f", "-s", "KILL")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new QueryCommand.Builder()
					.argv("squeue", "--noheader", "--Format=jobid,state", "--job", "{{ jobids|join(',') }}")
					.appendJobIds(false)
					.parser(new LineRegexParser(Pattern.compile("^(?<jobid>\\d+)\\s+(?<state>\\w+)\\s*$"), "jobid", 0))
					.mapState("BOOT_FAIL", "Disconnected")
					.mapState("CANCELLED", "Disconnected")
					.mapState("COMPLETED", "Disconnected")
					.mapState("CONFIGURING", "Launching")
					.mapState("COMPLETING", "Connected")
					.mapState("DEADLINE", "Disconnected")
					.mapState("FAILED", "Disconnected")
					.mapState("NODE_FAIL", "Disconnected")
					.mapState("OUT_OF_MEMORY", "Disconnected")
					.mapState("PENDING", "Launching")
					.mapState("PREEMPTED", "Disconnected")
					.mapState("RUNNING", "Connected")
					.mapState("RESV_DEL_HOLD", "Launching")
					.mapState("REQUEUE_FED", "Launching")
					.mapState("REQUEUE_HOLD", "Launching")
					.mapState("REQUEUED", "Launching")
					.mapState("RESIZING", "Unknown")
					.mapState("REVOKED", "Unknown")
					.mapState("SIGNALING", "Unknown")
					.mapState("SPECIAL_EXIT", "Unknown")
					.mapState("STAGE_QUIT", "Connected")
					.mapState("STOPPED", "Launched")
					.mapState("SUSPENDED", "Launched")
					.mapState("TIMEOUT", "Disconnected")
					.build(),
			new String(ActuatorUtils.loadInternalFile(HPCDefinition.class, "hpc.slurm.j2"), StandardCharsets.UTF_8)
	);

	public static final HPCDefinition DEFINITION_LSF = new HPCDefinition(
			"lsf",
			new SubmitCommand.Builder()
					.argv("bsub", "-f", "{{ script_path }}")
					.parser(new LineRegexParser(Pattern.compile("^Job\\s+<(?<jobid>[^>]+)>.*$"), "jobid", 1))
					.build(),
			new DeleteCommand.Builder()
					.argv("bkill")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new DeleteCommand.Builder()
					.argv("bkill", "-s", "KILL")
					.appendJobIds(true)
					.parser(new NoopParser())
					.build(),
			new QueryCommand.Builder()
					.argv("true")
					.parser(new NoopParser())
					.build(),
			new String(ActuatorUtils.loadInternalFile(HPCDefinition.class, "hpc.lsf.j2"), StandardCharsets.UTF_8)
	);

	static Map<String, HPCDefinition> INBUILT_DEFINITIONS = Map.of(
			DEFINITION_PBSPRO.name, DEFINITION_PBSPRO,
			DEFINITION_SLURM.name, DEFINITION_SLURM,
			DEFINITION_LSF.name, DEFINITION_LSF
	);

	public static HPCDefinition getInbuiltDefinition(String name) {
		return INBUILT_DEFINITIONS.get(name);
	}

	public final String name;
	public final SubmitCommand submit;
	public final DeleteCommand delete;
	public final DeleteCommand deleteForce;
	public final QueryCommand query;
	public final String submitTemplate;

	HPCDefinition(String name, SubmitCommand submit, DeleteCommand delete, DeleteCommand deleteForce, QueryCommand query, String submitTemplate) {
		this.name = name;
		this.submit = submit;
		this.delete = delete;
		this.deleteForce = deleteForce;
		this.query = query;
		this.submitTemplate = submitTemplate;
	}

	JsonObject toJson() {
		return Json.createObjectBuilder()
				.add("submit", submit.toJson()
						.add("template", submitTemplate)
				).add("delete", delete.toJson())
				.add("delete_force", deleteForce.toJson())
				.add("query", query.toJson())
				.build();
	}

	static HPCDefinition fromJson(String name, JsonObject jo, boolean validate) throws IOException {
		JsonObject jsub = jo.getJsonObject("submit");

		String submitTemplate;
		if(jsub.containsKey("template")) {
			submitTemplate = jsub.getString("template");
		} else if(jsub.containsKey("template_file")) {
			submitTemplate = Files.readString(Paths.get(jsub.getString("template_file")), StandardCharsets.UTF_8);
		} else if(jsub.containsKey("template_classpath")) {
			try(InputStream is = HPCDefinition.class.getClassLoader().getResourceAsStream(jsub.getString("template_classpath"))) {
				if(is == null) {
					throw new IOException("No such template in classpath");
				}
				submitTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		} else {
			/* Should be caught by the schema first. */
			throw new IllegalArgumentException();
		}

		if(validate) {
			/* Attempt a dummy render. */
			HPCActuator.renderTemplate(submitTemplate, HPCActuator.TEMPLATE_SAMPLE_VARS);
		}

		JsonObject jquery = jo.getJsonObject("query"); /* hue */

		return new HPCDefinition(
				name,
				SubmitCommand.fromJson(jsub).build(),
				DeleteCommand.fromJson(jo.getJsonObject("delete")).build(),
				DeleteCommand.fromJson(jo.getJsonObject("delete_force")).build(),
				QueryCommand.fromJson(jquery).build(),
				submitTemplate
		);
	}
}
