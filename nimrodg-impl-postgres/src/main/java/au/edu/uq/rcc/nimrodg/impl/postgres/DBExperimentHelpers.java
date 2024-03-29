/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.BrokenDBInvariantException;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBBaseHelper;
import au.edu.uq.rcc.nimrodg.impl.base.db.DBUtils;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempCommandResult;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempExperiment;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJob;
import au.edu.uq.rcc.nimrodg.impl.base.db.TempJobAttempt;

import javax.json.JsonString;
import javax.json.JsonValue;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Put all boilerplate relating to experiments, etc. into here.
 * <p>
 * Rule of thumb: If it ends in -Impl, it doesn't belong in here.
 */
public class DBExperimentHelpers extends DBBaseHelper {

	/* Experiments */
	private final PreparedStatement qGetExperiments;
	private final PreparedStatement qGetExperimentById;
	private final PreparedStatement qGetExperimentByName;
	private final PreparedStatement qDelExperimentById;
	private final PreparedStatement qDelExperimentByName;
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
	private final PreparedStatement qFilterJobAttempts;
	private final PreparedStatement qGetJobAttempt;

	private final PreparedStatement qFilterJobAttemptsByExperiment;
	private final PreparedStatement qAddCommandResult;
	private final PreparedStatement qGetCommandResultsByAttempt;

	/* Utility */
	private final PreparedStatement qAddCompiledExperiment;
	private final PreparedStatement qAddMultipleJobs;
	private final PreparedStatement qAddMultipleJobsInternal;

	public DBExperimentHelpers(Connection conn, List<PreparedStatement> statments) throws SQLException {
		super(conn, statments);

		this.qGetExperiments = prepareStatement("SELECT * FROM nimrod_full_experiments");
		this.qGetExperimentById = prepareStatement("SELECT * FROM nimrod_full_experiments WHERE id = ?");
		this.qGetExperimentByName = prepareStatement("SELECT * FROM nimrod_full_experiments WHERE name = ?");
		this.qDelExperimentById = prepareStatement("DELETE FROM nimrod_experiments WHERE id = ?");
		this.qDelExperimentByName = prepareStatement("DELETE FROM nimrod_experiments WHERE name = ?");
		this.qUpdateExperimentState = prepareStatement("UPDATE nimrod_experiments SET state = ?::nimrod_experiment_state WHERE id = ?");

		this.qGetSingleJob = prepareStatement("SELECT * FROM nimrod_full_jobs WHERE id = ?::BIGINT");

		this.qGetJobStatus = prepareStatement("SELECT * FROM get_job_status(?::BIGINT)");

		this.qFilterJobs = prepareStatement("SELECT * FROM filter_jobs(?::BIGINT, ?::nimrod_job_status[], ?::BIGINT, ?::BIGINT)");
		this.qGetJobsById = prepareStatement("SELECT * FROM nimrod_full_jobs WHERE id = ANY(?::BIGINT[])");

		this.qCreateJobAttempt = prepareStatement("SELECT * FROM create_job_attempt(?::BIGINT, ?::UUID)");
		this.qStartJobAttempt = prepareStatement("SELECT * FROM start_job_attempt(?::BIGINT, ?::UUID)");
		this.qFinishJobAttempt = prepareStatement("SELECT * FROM finish_job_attempt(?::BIGINT, ?::BOOLEAN)");
		this.qFilterJobAttempts = prepareStatement(
				"SELECT\n" +
				"    *\n" +
				"FROM\n" +
				"    nimrod_job_attempts\n" +
				"WHERE\n" +
				"    job_id = ANY(?::BIGINT[]) AND\n" +
				"    status = ANY(COALESCE(?::nimrod_job_status[], enum_range(NULL::nimrod_job_status)))\n" +
				";");
		this.qGetJobAttempt = prepareStatement("SELECT * FROM nimrod_job_attempts WHERE id = ?");

		this.qFilterJobAttemptsByExperiment = prepareStatement("SELECT * FROM filter_job_attempts_by_experiment(?::BIGINT, ?::nimrod_job_status[])");

		this.qAddCommandResult = prepareStatement("SELECT * FROM add_command_result(?::BIGINT, ?::nimrod_command_result_status, ?::BIGINT, ?::REAL, ?::INT, ?::TEXT, ?::INT, ?::BOOLEAN)");
		this.qGetCommandResultsByAttempt = prepareStatement("SELECT * FROM nimrod_command_results WHERE attempt_id = ANY(?::BIGINT[])");

		this.qAddCompiledExperiment = prepareStatement("SELECT * FROM add_compiled_experiment(?::TEXT, ?::TEXT, ?::jsonb)");
		this.qAddMultipleJobs = prepareStatement("SELECT * FROM add_multiple_jobs(?::BIGINT, ?::JSONB)");
		this.qAddMultipleJobsInternal = prepareStatement("SELECT job_id FROM add_multiple_jobs_internal(?::BIGINT, ?::JSONB) AS job_id");
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
		qGetExperimentById.setLong(1, id);
		try(ResultSet rs = qGetExperimentById.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(expFromRow(rs));
		}
	}

	public Optional<TempExperiment> getExperiment(String name) throws SQLException {
		qGetExperimentByName.setString(1, name);
		try(ResultSet rs = qGetExperimentByName.executeQuery()) {
			if(!rs.next()) {
				return Optional.empty();
			}

			return Optional.of(expFromRow(rs));
		}
	}

	public boolean deleteExperiment(long id) throws SQLException {
		qDelExperimentById.setLong(1, id);
		return qDelExperimentById.execute();
	}

	public boolean deleteExperiment(String name) throws SQLException {
		qDelExperimentByName.setString(1, name);
		return qDelExperimentByName.execute();
	}

	/*
	 * add_multiple_jobs() returns nimrod_full_jobs rows, these are extremely
	 * slow to fetch. the *_internal() variant just returns BIGINTs.
	 *
	 * It halves the insertion time.
	 */
	private void addMultipleJobsInternalJson(long expId, List<JsonValue> jobs) throws SQLException {
		qAddMultipleJobsInternal.setLong(1, expId);
		qAddMultipleJobsInternal.setString(2, jobs.toString());

		//long dbStart = System.currentTimeMillis();
		try(ResultSet rs = qAddMultipleJobsInternal.executeQuery()) {
			/* nop */
		}
		//float dbsec = (System.currentTimeMillis() - dbStart) / 1000.0f;
		//System.err.printf("  DB took %f sec\n", dbsec);
	}

	public TempExperiment addCompiledExperiment(String name, String workDir, CompiledRun exp) throws SQLException {
		boolean batched = exp.numJobs > 100000;
		int batchSize = 100000;

		qAddCompiledExperiment.setString(1, name);
		qAddCompiledExperiment.setString(2, workDir);
		qAddCompiledExperiment.setString(3, JsonUtils.toJson(exp, !batched).toString());

		TempExperiment te;
		try(ResultSet rs = qAddCompiledExperiment.executeQuery()) {
			if(!rs.next()) {
				throw new BrokenDBInvariantException("add_compiled_experiment() returned no rows.");
			}

			te = expFromRow(rs);
		}

		if(!batched) {
			return te;
		}

		//AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
		try {
			JsonUtils.withJobBatches(exp, batchSize, j -> {
				try {
					this.addMultipleJobsInternalJson(te.id, j);
				} catch(SQLException e) {
					throw new RuntimeException(e);
				}

				//System.err.printf("Batch done: %f sec\n", (System.currentTimeMillis() - startTime.get()) / 1000.0f);
				//startTime.set(System.currentTimeMillis());
			});
		} catch(RuntimeException e) {
			if(e.getCause() instanceof SQLException) {
				throw (SQLException)e.getCause();
			} else {
				throw e;
			}
		}

		return te;
	}


	public void updateExperimentState(long expId, Experiment.State state) throws SQLException {
		qUpdateExperimentState.setString(1, Experiment.stateToString(state));
		qUpdateExperimentState.setLong(2, expId);
		qUpdateExperimentState.execute();
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
		qFilterJobs.setArray(2, conn.createArrayOf("TEXT", status.stream()
				.filter(Objects::nonNull)
				.map(Enum::toString)
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

		Set<Set<String>> sss = jobs.stream().map(Map::keySet).collect(Collectors.toSet());
		if(sss.size() != 1) {
			throw new IllegalArgumentException("Mismatching key sets.");
		}

		qAddMultipleJobs.setLong(1, expId);
		qAddMultipleJobs.setString(2, JsonUtils.buildJobsJson(jobs).toString());

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

	public List<TempJobAttempt> filterJobAttempts(Collection<Long> ids, EnumSet<JobAttempt.Status> status) throws SQLException {
		qFilterJobAttempts.setArray(1, conn.createArrayOf("BIGINT", ids.toArray()));
		qFilterJobAttempts.setArray(2, conn.createArrayOf("TEXT", status.stream()
				.filter(Objects::nonNull)
				.map(Enum::toString)
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
				throw new BrokenDBInvariantException("no such job attempt");
			}

			return attemptFromRow(rs);
		}
	}

	public List<TempJobAttempt> filterJobAttemptsByExperiment(long expId, EnumSet<JobAttempt.Status> status) throws SQLException {
		qFilterJobAttemptsByExperiment.setLong(1, expId);
		qFilterJobAttemptsByExperiment.setArray(2, conn.createArrayOf("TEXT", status.stream()
				.filter(Objects::nonNull)
				.map(Enum::toString)
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

	public List<TempCommandResult> getCommandResultsByAttempt(Collection<Long> attemptIds) throws SQLException {
		qGetCommandResultsByAttempt.setArray(1, conn.createArrayOf("BIGINT", attemptIds.toArray()));

		List<TempCommandResult> crs = new ArrayList<>();
		try(ResultSet rs = qGetCommandResultsByAttempt.executeQuery()) {
			while(rs.next()) {
				crs.add(commandResultFromRow(rs));
			}
		}

		return crs;
	}

	private static TempExperiment expFromRow(ResultSet rs) throws SQLException {
		return new TempExperiment(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("work_dir"),
				Experiment.stringToState(rs.getString("state")),
				DBUtils.getInstant(rs, "created"),
				JsonUtils.stringListFromJson(DBUtils.getJSONArray(rs, "vars_json")),
				JsonUtils.taskListFromJson(DBUtils.getJSONObject(rs, "tasks_json"))
		);
	}

	private static TempJob jobFromRow(ResultSet rs) throws SQLException {
		return new TempJob(
				rs.getLong("id"),
				rs.getLong("exp_id"),
				rs.getLong("job_index"),
				DBUtils.getInstant(rs, "created"),
				JobAttempt.stringToStatus(rs.getString("status")),
				DBUtils.getJSONObject(rs, "full_variables").entrySet().stream()
						.collect(Collectors.toMap(Map.Entry::getKey, e -> ((JsonString)e.getValue()).getString()))
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
				_uuid == null ? null : UUID.fromString(_uuid)
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
