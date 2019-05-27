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
package au.edu.uq.rcc.nimrodg.impl.postgres;

import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledJob;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.impl.base.db.BrokenDBInvariantException;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBBaseHelper;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempCommandResult;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempExperiment;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJob;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJobAttempt;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;

/**
 * Put all boilerplate relating to experiments, etc. into here.
 *
 * Rule of thumb: If it ends in -Impl, it doesn't belong in here.
 */
public class DBExperimentHelpers extends DBBaseHelper {

	/* Experiments */
	private final PreparedStatement qGetExperiments;
	private final PreparedStatement qGetExperiment;
	private final PreparedStatement qDelExperiment;
	private final PreparedStatement qUpdateExperimentState;

	/* Jobs */
	private final PreparedStatement qGetSingleJob;
	private final PreparedStatement qGetJobStatus;
	private final PreparedStatement qFilterJobs;
	private final PreparedStatement qGetJobsById;

	/* Job Attempts */
	private final PreparedStatement qCreateJobAttempt;
	private final PreparedStatement qStartJobAttempt;
	private final PreparedStatement qFinishJobAttempt;
	private final PreparedStatement qGetJobAttempts;
	private final PreparedStatement qFilterJobAttempts;
	private final PreparedStatement qGetJobAttempt;

	private final PreparedStatement qFilterJobAttemptsByExperiment;
	private final PreparedStatement qAddCommandResult;

	/* Utility */
	private final PreparedStatement qAddCompiledExperiment;
	private final PreparedStatement qAddCompiledExperimentForBatch;
	private final PreparedStatement qAddMultipleJobs;
	private final PreparedStatement qAddMultipleJobsInternal;

	private final PreparedStatement qIsTokenValidForStorage;

	public DBExperimentHelpers(Connection conn, List<PreparedStatement> statments) throws SQLException {
		super(conn, statments);

		this.qGetExperiments = prepareStatement("SELECT * FROM get_experiments()");
		this.qGetExperiment = prepareStatement("SELECT * FROM get_experiment(?)");
		this.qDelExperiment = prepareStatement("SELECT delete_experiment(?::BIGINT)");
		this.qUpdateExperimentState = prepareStatement("SELECT update_experiment_state(?::BIGINT, ?::nimrod_experiment_state)");

		this.qGetSingleJob = prepareStatement("SELECT * FROM nimrod_full_jobs WHERE id = ?::BIGINT");

		this.qGetJobStatus = prepareStatement("SELECT * FROM get_job_status(?::BIGINT)");

		this.qFilterJobs = prepareStatement("SELECT * FROM filter_jobs(?::BIGINT, ?::nimrod_job_status[], ?::BIGINT, ?::BIGINT)");
		this.qGetJobsById = prepareStatement("SELECT * FROM get_jobs_by_id(?::BIGINT[])");

		this.qCreateJobAttempt = prepareStatement("SELECT * FROM create_job_attempt(?::BIGINT, ?::UUID)");
		this.qStartJobAttempt = prepareStatement("SELECT * FROM start_job_attempt(?::BIGINT, ?::UUID)");
		this.qFinishJobAttempt = prepareStatement("SELECT * FROM finish_job_attempt(?::BIGINT, ?::BOOLEAN)");
		this.qGetJobAttempts = prepareStatement("SELECT * FROM get_job_attempts(?::BIGINT)");
		this.qFilterJobAttempts = prepareStatement("SELECT * FROM filter_job_attempts(?::BIGINT, ?::nimrod_job_status[])");
		this.qGetJobAttempt = prepareStatement("SELECT * FROM get_job_attempt(?::BIGINT)");

		this.qFilterJobAttemptsByExperiment = prepareStatement("SELECT * FROM filter_job_attempts_by_experiment(?::BIGINT, ?::nimrod_job_status[])");

		this.qAddCommandResult = prepareStatement("SELECT * FROM add_command_result(?::BIGINT, ?::nimrod_command_result_status, ?::BIGINT, ?::REAL, ?::INT, ?::TEXT, ?::INT, ?::BOOLEAN)");

		this.qAddCompiledExperiment = prepareStatement("SELECT * FROM add_compiled_experiment(?::TEXT, ?::TEXT, ?::TEXT, ?::jsonb)");
		this.qAddCompiledExperimentForBatch = prepareStatement("SELECT * FROM add_compiled_experiment_for_batch(?::TEXT, ?::TEXT, ?::TEXT, ?::TEXT[], ?::jsonb)");
		this.qAddMultipleJobs = prepareStatement("SELECT * FROM add_multiple_jobs(?::BIGINT, ?::TEXT[], ?::jsonb)");
		this.qAddMultipleJobsInternal = prepareStatement("SELECT job_id FROM add_multiple_jobs_internal(?::BIGINT, ?::TEXT[], ?::JSONB) AS job_id");

		this.qIsTokenValidForStorage = prepareStatement("SELECT is_token_valid_for_experiment_storage(?::BIGINT, ?::TEXT) AS id");
	}

	public List<TempExperiment> listExperiments() throws SQLException {
		List<TempExperiment> s = new ArrayList<>();
		try(ResultSet rs = qGetExperiments.executeQuery()) {
			while(rs.next()) {
				s.add(expFromRow(rs));
			}
		}

		return s;
	}

	public Optional<TempExperiment> getExperiment(long id) throws SQLException {
		qGetExperiment.setLong(1, id);
		try(ResultSet rs = qGetExperiment.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(expFromRow(rs));
		}
	}

	public Optional<TempExperiment> getExperiment(String name) throws SQLException {
		qGetExperiment.setString(1, name);
		try(ResultSet rs = qGetExperiment.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(expFromRow(rs));
		}
	}

	public boolean experimentExists(String name) throws SQLException {
		return getExperiment(name).isPresent();
	}

	public boolean experimentExists(long id) throws SQLException {
		return getExperiment(id).isPresent();
	}

	public boolean deleteExperiment(long id) throws SQLException {
		qDelExperiment.setLong(1, id);
		return qDelExperiment.execute();
	}

	public boolean deleteExperiment(String name) throws SQLException {
		qDelExperiment.setString(1, name);
		return qDelExperiment.execute();
	}

	/*
	 * add_multiple_jobs() returns nimrod_full_jobs rows, these are extermely
	 * slow to fetch. the *_internal() variant just returns BIGINTs.
	 */
	private void addMultipleJobsInternal(long expId, Array vars, String jsonValues) throws SQLException {
		//long dbStart = System.currentTimeMillis();
		qAddMultipleJobsInternal.setLong(1, expId);
		qAddMultipleJobsInternal.setArray(2, vars);
		qAddMultipleJobsInternal.setString(3, jsonValues);
		try(ResultSet rs = qAddMultipleJobsInternal.executeQuery()) {
			/* nop */
		}
		//float dbsec = (System.currentTimeMillis() - dbStart) / 1000.0f;
		//System.err.printf("  DB took %f sec\n", dbsec);
	}

	private TempExperiment addCompiledExperimentBatched(String name, String workDir, String fileToken, CompiledRun exp) throws SQLException {
		/* Add the base run */
		qAddCompiledExperimentForBatch.setString(1, name);
		qAddCompiledExperimentForBatch.setString(2, workDir);
		qAddCompiledExperimentForBatch.setString(3, fileToken);
		String[] varNames = exp.variables.stream().map(cv -> cv.name).toArray(String[]::new);
		Array vars = conn.createArrayOf("TEXT", varNames);
		qAddCompiledExperimentForBatch.setArray(4, vars);
		qAddCompiledExperimentForBatch.setString(5, JsonUtils.toJson(exp.tasks).toString());

		TempExperiment te;
		try(ResultSet rs = qAddCompiledExperimentForBatch.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("add_compiled_experiment_for_batch() returned no rows.");
			}

			te = expFromRow(rs);
		}

		/* Now add the jobs */
		int batchSize = 100000;
		int i = 0;
		JsonArrayBuilder ja = Json.createArrayBuilder();
		//long startTime = System.currentTimeMillis();
		for(CompiledJob cj : exp.jobs) {
			String[] values = new String[varNames.length];
			for(int j = 0; j < values.length; ++j) {
				values[j] = exp.variables.get(j).values.get(cj.indices[j]);
			}

			ja.add(Json.createArrayBuilder(List.of(values)));
			++i;

			if(i == batchSize) {
				//System.err.printf("Batch done: %f sec\n", (System.currentTimeMillis() - startTime) / 1000.0f);
				addMultipleJobsInternal(te.id, vars, ja.build().toString());
				i = 0;
				//startTime = System.currentTimeMillis();
				ja = Json.createArrayBuilder();
			}
		}

		//System.err.printf("TRAILING one\n");
		JsonArray jaa = ja.build();
		if(!jaa.isEmpty()) {
			addMultipleJobsInternal(te.id, vars, jaa.toString());
		}

		return te;
	}

	public TempExperiment addCompiledExperiment(String name, String workDir, String fileToken, CompiledRun exp) throws SQLException {
		/* If there's too many jobs to dump in one go, stream them in. */
		if(exp.numJobs > 100000) {
			return addCompiledExperimentBatched(name, workDir, fileToken, exp);
		}

		qAddCompiledExperiment.setString(1, name);
		qAddCompiledExperiment.setString(2, workDir);
		qAddCompiledExperiment.setString(3, fileToken);
		qAddCompiledExperiment.setString(4, JsonUtils.toJson(exp).toString());

		try(ResultSet rs = qAddCompiledExperiment.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("add_compiled_experiment() returned no rows.");
			}

			return expFromRow(rs);
		}
	}

	public void updateExperimentState(long expId, Experiment.State state) throws SQLException {
		qUpdateExperimentState.setLong(1, expId);
		qUpdateExperimentState.setString(2, Experiment.stateToString(state));

		try(ResultSet rs = qUpdateExperimentState.executeQuery()) {
			// nop
		}
	}

	public Optional<TempJob> getSingleJob(long id) throws SQLException {
		qGetSingleJob.setLong(1, id);
		try(ResultSet rs = qGetSingleJob.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(jobFromRow(rs));
		}
	}

	public JobAttempt.Status getJobStatus(long jobId) throws SQLException {
		qGetJobStatus.setLong(1, jobId);

		try(ResultSet rs = qGetJobStatus.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("get_job_status() returned no rows.");
			}

			return JobAttempt.stringToStatus(rs.getString(1));
		}
	}

	public List<TempJob> filterJobs(long expId, EnumSet<JobAttempt.Status> status, long start, long limit) throws SQLException {
		qFilterJobs.setLong(1, expId);
		qFilterJobs.setArray(2, conn.createArrayOf("nimrod_job_status", status.stream()
				.filter(s -> s != null)
				.map(s -> s.toString())
				.toArray()));
		qFilterJobs.setObject(3, start < 0 ? null : start);
		qFilterJobs.setObject(4, limit <= 0 ? null : limit);

		List<TempJob> j = new ArrayList<>();
		try(ResultSet rs = qFilterJobs.executeQuery()) {
			while(rs.next()) {
				j.add(jobFromRow(rs));
			}
		}

		return j;
	}

	public List<TempJob> getJobsById(long[] ids) throws SQLException {
		qGetJobsById.setArray(1, conn.createArrayOf("BIGINT", Arrays.stream(ids)
				.boxed().toArray(Long[]::new)
		));

		List<TempJob> j = new ArrayList<>();
		try(ResultSet rs = qGetJobsById.executeQuery()) {
			while(rs.next()) {
				j.add(jobFromRow(rs));
			}
		}

		return j;
	}

	public List<TempJob> addJobs(long expId, Collection<Map<String, String>> jobs) throws SQLException {
		if(jobs.isEmpty()) {
			return List.of();
		}

		Set<Set<String>> sss = jobs.stream().map(j -> j.keySet()).collect(Collectors.toSet());
		if(sss.size() != 1) {
			throw new IllegalArgumentException("Mismatching key sets.");
		}

		String[] vars = sss.stream().flatMap(s -> s.stream()).toArray(String[]::new);

		JsonArrayBuilder values = Json.createArrayBuilder();
		jobs.stream()
				.map(j -> Arrays.stream(vars).map(v -> j.get(v)).collect(Collectors.toList()))
				.map(j -> Json.createArrayBuilder(j))
				.forEach(j -> values.add(j));

		Array varNames = conn.createArrayOf("TEXT", vars);

		qAddMultipleJobs.setLong(1, expId);
		qAddMultipleJobs.setArray(2, varNames);
		qAddMultipleJobs.setString(3, values.build().toString());

		List<TempJob> _jobs = new ArrayList<>(jobs.size());
		try(ResultSet rs = qAddMultipleJobs.executeQuery()) {
			while(rs.next()) {
				_jobs.add(jobFromRow(rs));
			}
		}

		return _jobs;
	}

	public TempJobAttempt createJobAttempt(long jobId, UUID uuid) throws SQLException {
		qCreateJobAttempt.setLong(1, jobId);
		qCreateJobAttempt.setString(2, uuid.toString());

		try(ResultSet rs = qCreateJobAttempt.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("create_job_attempt() returned no rows.");
			}

			return attemptFromRow(rs);
		}
	}

	public TempJobAttempt startJobAttempt(long jobId, UUID agentUuid) throws SQLException {
		qStartJobAttempt.setLong(1, jobId);
		qStartJobAttempt.setString(2, agentUuid.toString());

		try(ResultSet rs = qStartJobAttempt.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("start_job_attempt() returned no rows.");
			}

			return attemptFromRow(rs);
		}
	}

	public TempJobAttempt finishJobAttempt(long attId, boolean failed) throws SQLException {
		qFinishJobAttempt.setLong(1, attId);
		qFinishJobAttempt.setBoolean(2, failed);

		try(ResultSet rs = qFinishJobAttempt.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("finish_job_attempt() returned no rows.");
			}

			return attemptFromRow(rs);
		}
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

	public List<TempJobAttempt> filterJobAttempts(long jobId, EnumSet<JobAttempt.Status> status) throws SQLException {
		qFilterJobAttempts.setLong(1, jobId);
		qFilterJobAttempts.setArray(2, conn.createArrayOf("nimrod_job_status", status.stream()
				.filter(s -> s != null)
				.map(s -> s.toString())
				.toArray()));

		List<TempJobAttempt> atts = new ArrayList<>();
		try(ResultSet rs = qFilterJobAttempts.executeQuery()) {
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

	public long isTokenValidForStorage(long expId, String token) throws SQLException {
		qIsTokenValidForStorage.setLong(1, expId);
		qIsTokenValidForStorage.setString(2, token);

		try(ResultSet rs = qIsTokenValidForStorage.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("is_token_valid_for_experiment_storage() returned no rows.");
			}

			return rs.getLong("id");
		}
	}

	public List<TempJobAttempt> filterJobAttemptsByExperiment(long expId, EnumSet<JobAttempt.Status> status) throws SQLException {
		qFilterJobAttemptsByExperiment.setLong(1, expId);
		qFilterJobAttemptsByExperiment.setArray(2, conn.createArrayOf("nimrod_job_status", status.stream()
				.filter(s -> s != null)
				.map(s -> s.toString())
				.toArray()));

		List<TempJobAttempt> atts = new ArrayList<>();
		try(ResultSet rs = qFilterJobAttemptsByExperiment.executeQuery()) {
			while(rs.next()) {
				atts.add(attemptFromRow(rs));
			}
		}

		return atts;
	}

	public TempCommandResult addCommandResult(long attId, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errCode, boolean stop) throws SQLException {
		qAddCommandResult.setLong(1, attId);
		qAddCommandResult.setString(2, CommandResult.statusToString(status));
		qAddCommandResult.setLong(3, index);
		qAddCommandResult.setFloat(4, time);
		qAddCommandResult.setInt(5, retval);
		qAddCommandResult.setString(6, message);
		qAddCommandResult.setInt(7, errCode);
		qAddCommandResult.setBoolean(8, stop);

		try(ResultSet rs = qAddCommandResult.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("add_command_result() returned no rows.");
			}

			return commandResultFromRow(rs);
		}
	}

	private static TempExperiment expFromRow(ResultSet rs) throws SQLException {
		return new TempExperiment(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("work_dir"),
				Experiment.stringToState(rs.getString("state")),
				DBUtils.getInstant(rs, "created"),
				rs.getString("file_token"),
				rs.getString("path"),
				JsonUtils.stringListFromJson(DBUtils.getJSONArray(rs, "vars_json")),
				JsonUtils.taskListFromJson(DBUtils.getJSONObject(rs, "tasks_json"))
		);
	}

	private static TempJob jobFromRow(ResultSet rs) throws SQLException {

		String[] keys = (String[])rs.getArray("var_names").getArray();
		String[] values = (String[])rs.getArray("var_values").getArray();

		Map<String, String> vars = new HashMap<>();
		for(int i = 0; i < keys.length; ++i) {
			vars.put(keys[i], values[i]);
		}

		return new TempJob(
				rs.getLong("id"),
				rs.getLong("exp_id"),
				rs.getLong("job_index"),
				DBUtils.getInstant(rs, "created"),
				rs.getString("path"),
				vars
		);
	}

	private static TempJobAttempt attemptFromRow(ResultSet rs) throws SQLException {
		String _uuid = rs.getString("agent_uuid");
		return new TempJobAttempt(
				rs.getLong("id"),
				rs.getLong("job_id"),
				UUID.fromString(rs.getString("UUID")),
				JobAttempt.stringToStatus(rs.getString("status")),
				DBUtils.getInstant(rs, "creation_time"),
				DBUtils.getInstant(rs, "start_time"),
				DBUtils.getInstant(rs, "finish_time"),
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
