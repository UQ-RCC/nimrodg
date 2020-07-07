/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.impl.sqlite3;

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.Substitution;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;
import au.edu.uq.rcc.nimrodg.api.utils.run.CommandArgumentBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledArgument;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.TaskBuilder;
import au.edu.uq.rcc.nimrodg.impl.base.db.BrokenDBInvariantException;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBBaseHelper;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempCommandResult;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempExperiment;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJob;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJobAttempt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NB: At time of writing, the SQLite3 JDBC driver doesn't support getGeneratedKeys() with batching.
 */
public class DBExperimentHelpers extends DBBaseHelper {

	private final PreparedStatement qGetReservedVariables;

	private final PreparedStatement qInsertExperiment;
	private final PreparedStatement qInsertVariable;
	private final PreparedStatement qInsertTask;
	private final PreparedStatement qInsertCommand;
	private final PreparedStatement qInsertArgument;
	private final PreparedStatement qInsertSubstitution;
	private final PreparedStatement qInsertJob;

	private final PreparedStatement qGetExperiments;
	private final PreparedStatement qGetExperimentById;
	private final PreparedStatement qGetExperimentByName;
	private final PreparedStatement qGetExperimentUserVariables;
	private final PreparedStatement qGetExperimentTasks;
	private final PreparedStatement qGetTaskCommands;
	private final PreparedStatement qGetCommandArguments;
	private final PreparedStatement qGetArgumentSubstitutions;

	private final PreparedStatement qDeleteExperimentById;
	private final PreparedStatement qDeleteExperimentByName;

	private final PreparedStatement qUpdateExperimentState;

	private final PreparedStatement qGetSingleJob;
	private final PreparedStatement qGetJobRange;
	private final PreparedStatement qGetJobAttempts;
	private final PreparedStatement qGetNextJobId;

	private final PreparedStatement qCreateJobAttempt;
	private final PreparedStatement qStartJobAttempt;
	private final PreparedStatement qFinishJobAttempt;
	private final PreparedStatement qGetJobAttempt;
	private final PreparedStatement qFilterJobAttemptsByExperiment;

	private final PreparedStatement qAddCommandResult;
	private final PreparedStatement qGetCommandResult;

	private final PreparedStatement qGetCommandIdForResult;

	private final PreparedStatement qIsTokenValidForExperimentStorage;

	public DBExperimentHelpers(Connection conn, List<PreparedStatement> statements) throws SQLException {
		super(conn, statements);

		this.qGetReservedVariables = prepareStatement("SELECT name FROM nimrod_reserved_variables");

		this.qInsertExperiment = prepareStatement("INSERT INTO nimrod_experiments(name, work_dir, file_token, path) VALUES(?, ?, ?, ?)", true);
		this.qInsertVariable = prepareStatement("INSERT INTO nimrod_variables(exp_id, name) VALUES(?, ?)", true);
		this.qInsertTask = prepareStatement("INSERT INTO nimrod_tasks(exp_Id, name) VALUES(?, ?)", true);
		this.qInsertCommand = prepareStatement("INSERT INTO nimrod_commands(command_index, task_id, type) VALUES(?, ?, ?)", true);
		this.qInsertArgument = prepareStatement("INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text) VALUES(?, ?, ?)", true);
		this.qInsertSubstitution = prepareStatement(
				"INSERT INTO nimrod_substitutions(arg_id, variable_id, start_index, end_index, relative_start) VALUES(?, ?, ?, ?, ?)",
				true
		);
		this.qInsertJob = prepareStatement("INSERT INTO nimrod_jobs(exp_id, job_index, created, variables, path) VALUES(?, ?, ?, ?, ?)", true);

		this.qGetExperiments = prepareStatement("SELECT * FROM nimrod_experiments");
		this.qGetExperimentById = prepareStatement("SELECT * FROM nimrod_experiments WHERE id = ?");
		this.qGetExperimentByName = prepareStatement("SELECT * FROM nimrod_experiments WHERE name = ?");
		this.qGetExperimentUserVariables = prepareStatement("SELECT id, name FROM nimrod_user_variables WHERE exp_id = ?");
		this.qGetExperimentTasks = prepareStatement("SELECT id, name FROM nimrod_tasks WHERE exp_id = ?");
		this.qGetTaskCommands = prepareStatement("SELECT id, command_index, type FROM nimrod_commands WHERE task_id = ?");
		this.qGetCommandArguments = prepareStatement("SELECT id, arg_text FROM nimrod_command_arguments WHERE command_id = ? ORDER BY arg_index");
		this.qGetArgumentSubstitutions = prepareStatement("SELECT * FROM nimrod_full_substitutions WHERE arg_id = ?");

		this.qDeleteExperimentById = prepareStatement("DELETE FROM nimrod_experiments WHERE id = ?");
		this.qDeleteExperimentByName = prepareStatement("DELETE FROM nimrod_experiments WHERE name = ?");

		this.qUpdateExperimentState = prepareStatement("UPDATE nimrod_experiments SET state = ? WHERE id = ?");

		this.qGetSingleJob = prepareStatement("SELECT * FROM nimrod_jobs WHERE id = ?");
		this.qGetJobRange = prepareStatement("SELECT * FROM nimrod_jobs WHERE exp_id = ? AND job_index >= COALESCE(?, 0) ORDER BY job_index ASC LIMIT ?");
		this.qGetJobAttempts = prepareStatement("SELECT * FROM nimrod_job_attempts WHERE job_id = ?");

		/* The finer-grained filtering is done application-side, row by row. */
		this.qGetNextJobId = prepareStatement("SELECT COALESCE(MAX(job_index) + 1, 1) FROM nimrod_jobs WHERE exp_id = ?");

		this.qCreateJobAttempt = prepareStatement("INSERT INTO nimrod_job_attempts(job_id, uuid, token, path) VALUES(?, ?, ?, ?)", true);
		this.qStartJobAttempt = prepareStatement("UPDATE nimrod_job_attempts SET status = ?, agent_uuid = ? WHERE id = ?");
		this.qFinishJobAttempt = prepareStatement("UPDATE nimrod_job_attempts SET status = ? WHERE id = ?");
		this.qGetJobAttempt = prepareStatement("SELECT * FROM nimrod_job_attempts WHERE id = ?");
		this.qFilterJobAttemptsByExperiment = prepareStatement("	SELECT\n"
				+ "		att.*\n"
				+ "	FROM\n"
				+ "		nimrod_job_attempts AS att\n"
				+ "	INNER JOIN\n"
				+ "		nimrod_jobs AS j\n"
				+ "		ON j.id = att.job_id\n"
				+ "	WHERE\n"
				+ "		j.exp_id = ?\n"
				+ "	;");

		this.qAddCommandResult = prepareStatement("INSERT INTO nimrod_command_results(attempt_id, status, command_index, time, retval, message, error_code, stop, command_id) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)", true);
		this.qGetCommandResult = prepareStatement("SELECT * FROM nimrod_command_results WHERE id = ?");

		this.qIsTokenValidForExperimentStorage = prepareStatement("	SELECT r.* FROM (\n"
				+ "		SELECT\n"
				+ "			0 AS id\n"
				+ "		FROM\n"
				+ "			nimrod_experiments\n"
				+ "		WHERE\n"
				+ "			id = ?1 AND\n"
				+ "			file_token = ?2\n"
				+ "		UNION ALL\n"
				+ "		SELECT\n"
				+ "			att.id AS id\n"
				+ "		FROM\n"
				+ "			nimrod_job_attempts AS att,\n"
				+ "			nimrod_jobs AS j\n"
				+ "		WHERE\n"
				+ "			att.job_id = j.id AND\n"
				+ "			j.exp_id = ?1 AND\n"
				+ "			att.token = ?2\n"
				+ "		UNION ALL\n"
				+ "		SELECT -1 AS id\n"
				+ "	) AS r\n"
				+ "	ORDER BY id DESC\n"
				+ "	LIMIT 1\n"
				+ ";");

		this.qGetCommandIdForResult = prepareStatement("SELECT\n" +
				"    c.id AS id\n" +
				"FROM\n" +
				"    nimrod_job_attempts AS att,\n" +
				"    nimrod_jobs         AS j,\n" +
				"    nimrod_tasks        AS t,\n" +
				"    nimrod_commands     AS c\n" +
				"WHERE\n" +
				"    att.id          = ? AND\n" +
				"    j.id            = att.job_id AND\n" +
				"    t.exp_id        = j.exp_id AND\n" +
				"    t.name          = 'main' AND\n" +
				"    c.task_id       = t.id AND\n" +
				"    c.command_index = ?\n" +
				";");
	}

	public List<TempExperiment> listExperiments() throws SQLException {
		List<TempExperiment> exps = new ArrayList<>();
		try(ResultSet rs = qGetExperiments.executeQuery()) {
			while(rs.next()) {
				exps.add(experimentFromRow(rs));
			}
		}
		return exps;
	}

	private List<String> getExperimentUserVars(long expId) throws SQLException {
		List<String> vars = new ArrayList<>();
		qGetExperimentUserVariables.setLong(1, expId);
		try(ResultSet vrs = qGetExperimentUserVariables.executeQuery()) {
			while(vrs.next()) {
				vars.add(vrs.getString("name"));
			}
		}

		return vars;
	}

	private static class TmpCommand {

		public final long id;
		public final Command.Type type;
		public final List<CompiledArgument> args;

		public TmpCommand(long id, Command.Type type) {
			this.id = id;
			this.type = type;
			this.args = new ArrayList<>();
		}

	}

	private List<CompiledCommand> getTaskCommands(long taskId) throws SQLException {
		List<TmpCommand> cmds = new ArrayList<>();
		qGetTaskCommands.setLong(1, taskId);
		try(ResultSet rs = qGetTaskCommands.executeQuery()) {
			while(rs.next()) {
				cmds.add(new TmpCommand(rs.getLong("id"), Command.stringToCommandType(rs.getString("type"))));
			}
		}

		for(TmpCommand cmd : cmds) {
			Map<Long, CommandArgumentBuilder> args = new LinkedHashMap<>(); // Keep insertion order

			qGetCommandArguments.setLong(1, cmd.id);
			try(ResultSet rs = qGetCommandArguments.executeQuery()) {
				while(rs.next()) {
					args.put(rs.getLong("id"), new CommandArgumentBuilder(rs.getString("arg_text")));
				}
			}

			for(Long cid : args.keySet()) {
				CommandArgumentBuilder cb = args.get(cid);
				qGetArgumentSubstitutions.setLong(1, cid);
				try(ResultSet rs = qGetArgumentSubstitutions.executeQuery()) {
					while(rs.next()) {
						cb.addSubstitution(new CompiledSubstitution(
								rs.getString("name"),
								rs.getInt("start_index"),
								rs.getInt("end_index"),
								rs.getInt("relative_start")
						));
					}
				}

				cmd.args.add(cb.build());
			}
		}

		return cmds.stream()
				.map(c -> CompiledCommand.resolve(c.type, c.args))
				.collect(Collectors.toList());
	}

	private List<CompiledTask> getExperimentTasks(long expId) throws SQLException {
		Map<Long, TaskBuilder> taskBuilders = new HashMap<>();
		qGetExperimentTasks.setLong(1, expId);
		try(ResultSet rs = qGetExperimentTasks.executeQuery()) {
			while(rs.next()) {
				taskBuilders.put(rs.getLong("id"), new TaskBuilder().name(Task.stringToTaskName(rs.getString("name"))));
			}
		}

		for(Long l : taskBuilders.keySet()) {
			TaskBuilder b = taskBuilders.get(l);
			getTaskCommands(l).forEach(b::addCommand);
		}

		return taskBuilders.values().stream().map(TaskBuilder::build).collect(Collectors.toList());
	}

	public Optional<TempExperiment> getExperiment(long id) throws SQLException {
		qGetExperimentById.setLong(1, id);
		try(ResultSet rs = qGetExperimentById.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(experimentFromRow(rs));
		}
	}

	public Optional<TempExperiment> getExperiment(String name) throws SQLException {
		qGetExperimentByName.setString(1, name);
		try(ResultSet rs = qGetExperimentByName.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(experimentFromRow(rs));
		}
	}

	public boolean deleteExperiment(long id) throws SQLException {
		qDeleteExperimentById.setLong(1, id);
		return qDeleteExperimentById.executeUpdate() > 0;
	}

	public boolean deleteExperiment(String name) throws SQLException {
		qDeleteExperimentByName.setString(1, name);
		return qDeleteExperimentByName.executeUpdate() > 0;
	}

	private static String makeToken() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public TempExperiment addCompiledExperiment(String name, String workDir, String fileToken, CompiledRun exp) throws SQLException {
		if(fileToken == null) {
			fileToken = makeToken();
		}

		/* Check there's no reserved vars. */
		Set<String> reservedVars = getReservedVariables();
		Set<String> varNames = exp.variables.stream().map(v -> v.name).collect(Collectors.toSet());
		if(varNames.removeAll(reservedVars)) {
			throw new SQLException("Variables cannot have reserved names");
		}

		/* Merge the reserved vars with ours. */
		String[] names = Stream.concat(
				exp.variables.stream().map(v -> v.name),
				reservedVars.stream()
		).toArray(String[]::new);

		if(!workDir.endsWith("/")) {
			workDir = workDir + "/";
		}

		long expId = addExperiment(name, workDir, fileToken);

		/* Add the variables and build a lookup table. */
		long[] varIds = addVariables(expId, names);
		Map<String, Long> varLookupTable = new HashMap<>();
		for(int i = 0; i < varIds.length; ++i) {
			varLookupTable.put(names[i], varIds[i]);
		}

		/* Add the tasks */
		long[] taskIds = addTasks(expId, exp.tasks);

		{
			/* Add the commands */
			int i = 0;
			for(CompiledTask ct : exp.tasks) {
				long[] cmdIds = addCommands(taskIds[i++], ct.commands);

				/* Add the command arguments */
				int j = 0;
				for(CompiledCommand cmd : ct.commands) {
					List<CompiledArgument> normArgs = cmd.normalise();
					long[] argIds = addArguments(cmdIds[j], normArgs);

					/* Add the substitutions */
					int k = 0;
					for(CompiledArgument arg : normArgs) {
						addSubstitutions(argIds[k++], varLookupTable, arg.getSubstitutions());
					}
					++j;
				}

			}
		}

		addJobs2(expId, name, exp.buildJobsList(), 1);
		return getExperiment(expId).orElseThrow(IllegalStateException::new);
	}

	private Set<String> getReservedVariables() throws SQLException {
		Set<String> r = new HashSet<>();
		try(ResultSet rs = qGetReservedVariables.executeQuery()) {
			while(rs.next()) {
				r.add(rs.getString("name"));
			}
		}

		return r;
	}

	private long addExperiment(String name, String workDir, String fileToken) throws SQLException {
		/* Add the experiment */
		qInsertExperiment.setString(1, name);
		qInsertExperiment.setString(2, workDir);
		qInsertExperiment.setString(3, fileToken);
		qInsertExperiment.setString(4, name); // path

		int upd = qInsertExperiment.executeUpdate();
		if(upd == 0) {
			throw new SQLException("Creating experiment failed, no rows affected");
		}

		try(ResultSet rs = qInsertExperiment.getGeneratedKeys()) {
			if(rs.next()) {
				return rs.getLong(1);
			} else {
				throw new SQLException("Creating experiment failed, no id obtained");
			}
		}
	}

	private long[] addVariables(long expId, String[] names) throws SQLException {
		/* Add the variables. */
		long[] varIds = new long[names.length];
		for(int i = 0; i < names.length; ++i) {
			qInsertVariable.setLong(1, expId);
			qInsertVariable.setString(2, names[i]);
			if(qInsertVariable.executeUpdate() == 0) {
				throw new SQLException("Creating variable failed, no rows affected");
			}

			try(ResultSet rs = qInsertVariable.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating variable failed, no id obtained");
				}

				varIds[i] = rs.getLong(1);
			}
		}

		return varIds;
	}

	private long[] addTasks(long expId, List<CompiledTask> tasks) throws SQLException {
		long[] taskIds = new long[tasks.size()];
		int i = 0;
		for(CompiledTask ct : tasks) {
			qInsertTask.setLong(1, expId);
			qInsertTask.setString(2, Task.taskNameToString(ct.name));
			if(qInsertTask.executeUpdate() == 0) {
				throw new SQLException("Creating task failed, no rows affected");
			}

			try(ResultSet rs = qInsertTask.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating task failed, no id obtained");
				}

				taskIds[i] = rs.getLong(1);
			}

			++i;
		}

		return taskIds;
	}

	private long[] addCommands(long taskId, List<CompiledCommand> cmds) throws SQLException {
		long[] cmdIds = new long[cmds.size()];
		int i = 0;
		for(CompiledCommand cmd : cmds) {
			qInsertCommand.setLong(1, i);
			qInsertCommand.setLong(2, taskId);
			qInsertCommand.setString(3, Command.commandTypeToString(cmd.type));

			if(qInsertCommand.executeUpdate() == 0) {
				throw new SQLException("Creating command failed, no rows affected");
			}

			try(ResultSet rs = qInsertCommand.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating command failed, no id obtained");
				}

				cmdIds[i] = rs.getLong(1);
			}

			++i;
		}

		return cmdIds;
	}

	private long[] addArguments(long cmdId, List<CompiledArgument> args) throws SQLException {
		long[] argIds = new long[args.size()];
		int i = 0;
		for(CompiledArgument arg : args) {
			qInsertArgument.setLong(1, cmdId);
			qInsertArgument.setLong(2, i);
			qInsertArgument.setString(3, arg.getText());

			if(qInsertArgument.executeUpdate() == 0) {
				throw new SQLException("Creating argument failed, no rows affected");
			}

			try(ResultSet rs = qInsertArgument.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating argument failed, no id obtained");
				}

				argIds[i] = rs.getLong(1);
			}

			++i;
		}

		return argIds;
	}

	private long[] addSubstitutions(long argId, Map<String, Long> varMap, List<Substitution> subs) throws SQLException {
		long[] subIds = new long[subs.size()];
		int i = 0;
		for(Substitution sub : subs) {
			qInsertSubstitution.setLong(1, argId);
			qInsertSubstitution.setLong(2, varMap.get(sub.getVariable()));
			qInsertSubstitution.setInt(3, sub.getStartIndex());
			qInsertSubstitution.setInt(4, sub.getEndIndex());
			qInsertSubstitution.setInt(5, sub.getRelativeStartIndex());

			if(qInsertSubstitution.executeUpdate() == 0) {
				throw new SQLException("Creating substitution failed, no rows affected");
			}

			try(ResultSet rs = qInsertSubstitution.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating substitution failed, no id obtained");
				}

				subIds[i] = rs.getLong(1);
			}
			++i;
		}
		return subIds;
	}

	private long[] addJobs2(long expId, String expPath, Collection<Map<String, String>> jobs, long baseIndex) throws SQLException {
		long[] jobIds = new long[jobs.size()];
		/* Don't let Sqlite add the instant here, the NOW value isn't consistent within a transaction. */
		Instant now = Instant.now();

		int i = 0;
		for(Map<String, String> job : jobs) {
			qInsertJob.setLong(1, expId);
			qInsertJob.setLong(2, baseIndex + i);
			DBUtils.setLongInstant(qInsertJob, 3, now);
			qInsertJob.setString(4, JsonUtils.buildJobsJson(job).toString());
			qInsertJob.setString(5, String.format("%s/%d", expPath, baseIndex + i));
			if(qInsertJob.executeUpdate() == 0) {
				throw new SQLException("Creating job failed, no rows affected");
			}

			try(ResultSet rs = qInsertJob.getGeneratedKeys()) {
				if(!rs.next()) {
					throw new SQLException("Creating job failed, no id obtained");
				}

				jobIds[i] = rs.getLong(1);
			}
			++i;
		}

		return jobIds;
	}

	public void updateExperimentState(long expId, Experiment.State state) throws SQLException {
		qUpdateExperimentState.setString(1, Experiment.stateToString(state));
		qUpdateExperimentState.setLong(2, expId);
		qUpdateExperimentState.executeUpdate();
	}

	public List<TempJob> getJobRange(long expId, long start, long limit) throws SQLException {
		if(limit > Integer.MAX_VALUE) {
			throw new IllegalArgumentException();
		} else if(limit <= 0) {
			limit = Integer.MAX_VALUE;
		}

		qGetJobRange.setLong(1, expId);
		qGetJobRange.setLong(2, start <= 0 ? 1 : start);
		qGetJobRange.setLong(3, limit);

		List<TempJob> jobs = new ArrayList<>();
		try(ResultSet rs = qGetJobRange.executeQuery()) {
			while(rs.next()) {
				long jobIndex = rs.getLong("job_index");
				Map<String, String> vars = JsonUtils.jobFromJson(DBUtils.getJSONObject(rs, "variables"));
				vars.put("jobindex", String.valueOf(jobIndex));
				vars.put("jobname", String.valueOf(jobIndex));

				jobs.add(new TempJob(
						rs.getLong("id"),
						rs.getLong("exp_id"),
						jobIndex,
						DBUtils.getLongInstant(rs, "created"),
						rs.getString("path"),
						null,
						vars
				));
			}
		}

		return updateJobs(jobs);
	}

	private TempJob updateJob(TempJob tj) throws SQLException {
		return new TempJob(
				tj.id,
				tj.expId,
				tj.jobIndex,
				tj.created,
				tj.path,
				getJobCounts(tj.id).status,
				tj.variables
		);
	}

	private List<TempJob> updateJobs(List<TempJob> jobs) throws SQLException {
		List<TempJob> jobs2 = new ArrayList<>(jobs.size());
		for(TempJob tj : jobs) {
			jobs2.add(updateJob(tj));
		}

		return jobs2;
	}

	public Optional<TempJob> getSingleJob(long jobId) throws SQLException {
		qGetSingleJob.setLong(1, jobId);
		try(ResultSet rs = qGetSingleJob.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			long jobIndex = rs.getLong("job_index");
			Map<String, String> vars = JsonUtils.jobFromJson(DBUtils.getJSONObject(rs, "variables"));
			vars.put("jobindex", String.valueOf(jobIndex));
			vars.put("jobname", String.valueOf(jobIndex));

			return Optional.of(updateJob(new TempJob(
					jobId,
					rs.getLong("exp_id"),
					jobIndex,
					DBUtils.getLongInstant(rs, "created"),
					rs.getString("path"),
					null,
					vars
			)));
		}
	}

	public JobAttempt.Status getJobStatus(long jobId) throws SQLException {
		return getJobCounts(jobId).status;
	}

	private static class JobCounts {

		public final long jobId;
		public final long total;
		public final long notRun;
		public final long completed;
		public final long failed;
		public final long running;

		public final JobAttempt.Status status;

		public JobCounts(long jobId, long total, long notRun, long completed, long failed, long running) {
			this.jobId = jobId;
			this.total = total;
			this.notRun = notRun;
			this.completed = completed;
			this.failed = failed;
			this.running = running;

			if(total == 0 || total == notRun) {
				status = JobAttempt.Status.NOT_RUN;
			} else if(completed > 0) {
				status = JobAttempt.Status.COMPLETED;
			} else if(failed > 0 && completed == 0 && running == 0) {
				status = JobAttempt.Status.FAILED;
			} else if(running > 0 && completed == 0) {
				status = JobAttempt.Status.RUNNING;
			} else {
				throw new IllegalArgumentException();
			}
		}
	}

	private JobCounts getJobCounts(long jobId) throws SQLException {
		qGetJobAttempts.setLong(1, jobId);
		long total = 0, notRun = 0, completed = 0, failed = 0, running = 0;
		try(ResultSet rs = qGetJobAttempts.executeQuery()) {
			while(rs.next()) {
				JobAttempt.Status s = JobAttempt.stringToStatus(rs.getString("status"));
				switch(s) {
					case NOT_RUN:
						++notRun;
						break;
					case RUNNING:
						++running;
						break;
					case COMPLETED:
						++completed;
						break;
					case FAILED:
						++failed;
						break;
				}

				++total;
			}
		}

		return new JobCounts(jobId, total, notRun, completed, failed, running);
	}

	public List<TempJob> filterJobs(long expId, EnumSet<JobAttempt.Status> status, long start, long limit) throws SQLException {
		return getJobRange(expId, start, limit).stream()
				.filter(tj -> status.contains(tj.status))
				.collect(Collectors.toList());
	}

	public List<TempJob> getJobsById(long[] ids) throws SQLException {
		List<TempJob> jobs = new ArrayList<>(ids.length);
		for(long id : ids) {
			getSingleJob(id).ifPresent(jobs::add);
		}

		return jobs;
	}

	public List<TempJob> addJobs(long expId, String expPath, Collection<Map<String, String>> jobs) throws SQLException {
		long nextIndex;
		qGetNextJobId.setLong(1, expId);
		try(ResultSet rs = qGetNextJobId.executeQuery()) {
			if(!rs.next()) {
				throw new SQLException("Unable to get next job id, no rows returned.");
			}

			nextIndex = rs.getLong(1);
		}

		long[] ids = addJobs2(expId, expPath, jobs, nextIndex);
		return getJobRange(expId, nextIndex, ids.length);
	}

	public TempJobAttempt createJobAttempt(long jobId, String jobPath, UUID uuid) throws SQLException {
		qCreateJobAttempt.setLong(1, jobId);
		qCreateJobAttempt.setString(2, uuid.toString());
		qCreateJobAttempt.setString(3, makeToken());
		qCreateJobAttempt.setString(4, String.format("%s/%s", jobPath, uuid));

		if(qCreateJobAttempt.executeUpdate() == 0) {
			throw new SQLException("Creating job attempt failed, no rows affected");
		}

		long id;
		try(ResultSet rs = qCreateJobAttempt.getGeneratedKeys()) {
			if(!rs.next()) {
				throw new SQLException("Creating job attempt failed, no id obtained");
			}

			id = rs.getLong(1);
		}

		return getJobAttempt(id);
	}

	public TempJobAttempt startJobAttempt(long attId, UUID agentUuid) throws SQLException {
		qStartJobAttempt.setString(1, JobAttempt.statusToString(JobAttempt.Status.RUNNING));
		qStartJobAttempt.setString(2, agentUuid.toString());
		qStartJobAttempt.setLong(3, attId);

		if(qStartJobAttempt.executeUpdate() == 0) {
			throw new SQLException("Unable to start attempt, no rows affected.");
		}

		return getJobAttempt(attId);
	}

	public TempJobAttempt finishJobAttempt(long attId, boolean failed) throws SQLException {
		qFinishJobAttempt.setString(1, JobAttempt.statusToString(failed ? JobAttempt.Status.FAILED : JobAttempt.Status.COMPLETED));
		qFinishJobAttempt.setLong(2, attId);

		if(qFinishJobAttempt.executeUpdate() == 0) {
			throw new SQLException("Unable to start attempt, no rows affected.");
		}

		return getJobAttempt(attId);
	}

	public List<TempJobAttempt> getJobAttempts(long jobId) throws SQLException {
		qGetJobAttempts.setLong(1, jobId);

		List<TempJobAttempt> atts = new ArrayList<>();
		try(ResultSet rs = qGetJobAttempts.executeQuery()) {
			while(rs.next()) {
				atts.add(attemptFromRow(rs));
			}
		}

		return atts;
	}

	public TempJobAttempt getJobAttempt(long attId) throws SQLException {
		qGetJobAttempt.setLong(1, attId);

		try(ResultSet rs = qGetJobAttempt.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("get_job_attempt() returned no rows.");
			}

			return attemptFromRow(rs);
		}
	}

	public List<TempJobAttempt> filterJobAttemptsByExperiment(long expId, EnumSet<JobAttempt.Status> status) throws SQLException {
		qFilterJobAttemptsByExperiment.setLong(1, expId);
		List<TempJobAttempt> atts = new ArrayList<>();
		try(ResultSet rs = qFilterJobAttemptsByExperiment.executeQuery()) {
			while(rs.next()) {
				TempJobAttempt att = attemptFromRow(rs);
				if(status.contains(att.status)) {
					atts.add(att);
				}
			}
		}

		return atts;
	}

	private long getCommandIdForResult(long attemptId, long cmdIndex) throws SQLException {
		qGetCommandIdForResult.setLong(1, attemptId);
		qGetCommandIdForResult.setLong(2, cmdIndex);

		try(ResultSet rs = qGetCommandIdForResult.executeQuery()) {
			if(!rs.next()) {
				throw new SQLException("No such command");
			}
			return rs.getLong("id");
		}
	}

	public TempCommandResult addCommandResult(long attId, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errCode, boolean stop) throws SQLException {
		long cmdId = getCommandIdForResult(attId, index);

		qAddCommandResult.setLong(1, attId);
		qAddCommandResult.setString(2, CommandResult.statusToString(status));
		qAddCommandResult.setLong(3, index);
		qAddCommandResult.setFloat(4, time);
		qAddCommandResult.setInt(5, retval);
		qAddCommandResult.setString(6, message);
		qAddCommandResult.setInt(7, errCode);
		qAddCommandResult.setBoolean(8, stop);
		qAddCommandResult.setLong(9, cmdId);

		if(qAddCommandResult.executeUpdate() == 0) {
			throw new SQLException("Creating command result failed, no rows affected");
		}

		long id;
		try(ResultSet rs = qAddCommandResult.getGeneratedKeys()) {
			if(!rs.next()) {
				throw new SQLException("Creating command result failed, no id obtained");
			}
			id = rs.getLong(1);
		}

		qGetCommandResult.setLong(1, id);
		try(ResultSet rs = qGetCommandResult.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("add_command_result() returned no rows.");
			}

			return commandResultFromRow(rs);
		}
	}

	public long isTokenValidForStorage(long expId, String token) throws SQLException {
		qIsTokenValidForExperimentStorage.setLong(1, expId);
		qIsTokenValidForExperimentStorage.setString(2, token);

		try(ResultSet rs = qIsTokenValidForExperimentStorage.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("is_token_valid_for_experiment_storage() returned no rows.");
			}

			return rs.getLong("id");
		}
	}

	private TempExperiment experimentFromRow(ResultSet rs) throws SQLException {
		long expId = rs.getLong("id");
		return new TempExperiment(
				expId,
				rs.getString("name"),
				rs.getString("work_dir"),
				Experiment.stringToState(rs.getString("state")),
				DBUtils.getLongInstant(rs, "created"),
				rs.getString("file_token"),
				rs.getString("path"),
				getExperimentUserVars(expId),
				getExperimentTasks(expId)
		);
	}

	private static TempJobAttempt attemptFromRow(ResultSet rs) throws SQLException {
		String _uuid = rs.getString("agent_uuid");
		return new TempJobAttempt(
				rs.getLong("id"),
				rs.getLong("job_id"),
				UUID.fromString(rs.getString("UUID")),
				JobAttempt.stringToStatus(rs.getString("status")),
				DBUtils.getLongInstant(rs, "creation_time"),
				DBUtils.getLongInstant(rs, "start_time"),
				DBUtils.getLongInstant(rs, "finish_time"),
				rs.getString("token"),
				_uuid == null ? null : UUID.fromString(_uuid),
				rs.getString("path")
		);
	}

	private static TempCommandResult commandResultFromRow(ResultSet rs) throws SQLException {
		return new TempCommandResult(
				rs.getLong("id"),
				rs.getLong("attempt_id"),
				CommandResult.statusFromString(rs.getString("status")),
				rs.getLong("command_index"),
				rs.getFloat("time"),
				rs.getInt("retval"),
				rs.getString("message"),
				rs.getInt("error_code"),
				rs.getBoolean("stop")
		);
	}
}
