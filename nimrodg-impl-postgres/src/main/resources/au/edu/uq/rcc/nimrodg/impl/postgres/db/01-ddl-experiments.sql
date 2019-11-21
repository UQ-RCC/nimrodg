--
-- Nimrod/G
-- https://github.com/UQ-RCC/nimrodg
--
-- SPDX-License-Identifier: Apache-2.0
-- Copyright (c) 2019 The University of Queensland
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
/*
** Experiments/Jobs/Tasks/Variables
*/
DROP TYPE IF EXISTS nimrod_experiment_state CASCADE;
CREATE TYPE nimrod_experiment_state AS ENUM('STOPPED', 'STARTED', 'PERSISTENT');

DROP TABLE IF EXISTS nimrod_experiments CASCADE;
CREATE TABLE nimrod_experiments(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	name nimrod_identifier NOT NULL UNIQUE,
	state nimrod_experiment_state NOT NULL DEFAULT 'STOPPED'::nimrod_experiment_state,
	work_dir TEXT NOT NULL UNIQUE,
	created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
	file_token TEXT NOT NULL,
	-- NB: The variables are stored twice:
	-- - The first copy, here, is for quick lookups so we don't have to use nimrod_full_experiments.
	-- - The second copy, in nimrod_variables, is used for substitution validation so it contains
	--   the implicit ones too.
	variables TEXT[] NOT NULL,
	results TEXT[] NOT NULL,
	path nimrod_path NOT NULL UNIQUE
);
-- Use add_compiled_experiment() for adding. There is no facility for adding them manually.

CREATE OR REPLACE FUNCTION delete_experiment(_exp_id BIGINT) RETURNS SETOF VOID AS $$
	DELETE FROM nimrod_experiments WHERE id = _exp_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION delete_experiment(_name TEXT) RETURNS SETOF VOID AS $$
	DELETE FROM nimrod_experiments WHERE name = _name;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_experiment_state(_exp_id BIGINT, _state nimrod_experiment_state) RETURNS VOID AS $$
	UPDATE nimrod_experiments
	SET state = _state
	WHERE id = _exp_id;
$$ LANGUAGE SQL;


DROP TABLE IF EXISTS nimrod_jobs CASCADE;
CREATE TABLE nimrod_jobs(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	exp_id BIGINT NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	job_index BIGINT NOT NULL CHECK(job_index > 0),
	created TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
	-- NB: This isn't validated by a trigger because it's too damned slow to do so.
	-- Use add_compiled_experiment() or add_multiple_jobs()
	variables JSONB NOT NULL,
	path nimrod_path NOT NULL UNIQUE,
	UNIQUE(exp_id, job_index)
);

/*
** Set the creation date, workdir and path for a job when inserted.
*/
CREATE OR REPLACE FUNCTION _exp_t_job_add() RETURNS TRIGGER AS $$
DECLARE
	_path nimrod_path;
BEGIN
	SELECT path INTO _path FROM nimrod_experiments WHERE id = NEW.exp_id;
	NEW.path = _path || '/' || NEW.job_index::TEXT;
	RETURN NEW;
END $$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS t_exp_job_add ON nimrod_jobs;
CREATE TRIGGER t_exp_job_add BEFORE INSERT ON nimrod_jobs
	FOR EACH ROW EXECUTE PROCEDURE _exp_t_job_add();




DROP TABLE IF EXISTS nimrod_variables CASCADE;
CREATE TABLE nimrod_variables(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	exp_id BIGINT NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	name nimrod_variable_identifier NOT NULL,
	UNIQUE(exp_id, name)
);

DROP TYPE IF EXISTS nimrod_task_name CASCADE;
CREATE TYPE nimrod_task_name AS ENUM('nodestart', 'main');

DROP TABLE IF EXISTS nimrod_tasks CASCADE;
CREATE TABLE nimrod_tasks(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	exp_id BIGINT NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	name nimrod_task_name NOT NULL,
	UNIQUE(exp_id, name)
);

DROP TYPE IF EXISTS nimrod_command_type CASCADE;
CREATE TYPE nimrod_command_type AS ENUM('onerror', 'redirect', 'copy', 'exec');

DROP TABLE IF EXISTS nimrod_commands CASCADE;
CREATE TABLE nimrod_commands(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	command_index BIGINT NOT NULL CHECK(command_index >= 0),
	task_id BIGINT NOT NULL REFERENCES nimrod_tasks(id) ON DELETE CASCADE,
	type nimrod_command_type NOT NULL,
	UNIQUE(command_index, task_id)
);

DROP TABLE IF EXISTS nimrod_command_arguments CASCADE;
CREATE TABLE nimrod_command_arguments(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	command_id BIGINT NOT NULL REFERENCES nimrod_commands(id) ON DELETE CASCADE,
	arg_index BIGINT NOT NULL,
	arg_text TEXT NOT NULL,
	UNIQUE(arg_index, command_id)
);

DROP TABLE IF EXISTS nimrod_substitutions CASCADE;
CREATE TABLE nimrod_substitutions(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	arg_id BIGINT NOT NULL REFERENCES nimrod_command_arguments(id) ON DELETE CASCADE,
	variable_id BIGINT NOT NULL REFERENCES nimrod_variables(id) ON DELETE CASCADE,
	start_index INT NOT NULL CHECK(start_index >= 0),
	end_index INT NOT NULL CHECK(end_index > start_index),
	relative_start INT NOT NULL CHECK(relative_start >= 0)
);


DROP TYPE IF EXISTS nimrod_job_status CASCADE;
CREATE TYPE nimrod_job_status AS ENUM('NOT_RUN', 'RUNNING', 'COMPLETED', 'FAILED');

/*
** All the possible attempts at running a job.
** TODO: Add invariant conditions to this
*/
DROP TABLE IF EXISTS nimrod_job_attempts CASCADE;
CREATE TABLE nimrod_job_attempts(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	/* Job id */
	job_id BIGINT NOT NULL REFERENCES nimrod_jobs(id) ON DELETE CASCADE,
	uuid UUID NOT NULL UNIQUE,
	status nimrod_job_status NOT NULL DEFAULT 'NOT_RUN'::nimrod_job_status,
	creation_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
	start_time TIMESTAMP WITH TIME ZONE DEFAULT NULL,
	finish_time TIMESTAMP WITH TIME ZONE DEFAULT NULL,
	token TEXT NOT NULL DEFAULT _generate_random_token(32),
	path nimrod_path NOT NULL UNIQUE,
	/* Weak reference to the agent UUID. The agent may or may not exist */
	agent_uuid UUID/* REFERENCES nimrod_master_agents(id) */,
	CHECK(finish_time >= start_time),
	--CHECK(status != 'NOT_RUN'::nimrod_job_status)
	UNIQUE(job_id, token)
);

CREATE OR REPLACE FUNCTION _exp_t_attempt_add() RETURNS TRIGGER AS $$
DECLARE
	_path nimrod_path;
	_status nimrod_job_status;
BEGIN
	IF TG_OP = 'INSERT' THEN
		SELECT path INTO _path FROM nimrod_jobs WHERE id = NEW.job_id;
		NEW.path = _path || '/' || (SELECT replace(NEW.uuid::TEXT, '-', '_'));
	ELSIF TG_OP = 'UPDATE' THEN
		IF OLD.status = 'NOT_RUN'::nimrod_job_status AND NEW.status = 'RUNNING'::nimrod_job_status THEN
			-- NOT_RUN -> RUNNING
			NEW.start_time = NOW();
		ELSIF OLD.status = 'NOT_RUN'::nimrod_job_status AND NEW.status = 'FAILED'::nimrod_job_status THEN
			-- NOT_RUN -> FAILED
			NEW.start_time = NOW();
			NEW.finish_time = NOW();
		ELSIF OLD.status = 'RUNNING'::nimrod_job_status AND (NEW.status = 'FAILED'::nimrod_job_status OR NEW.status = 'COMPLETED'::nimrod_job_status) THEN
			-- RUNNING -> {FAILED, COMPLETED}
			NEW.finish_time = NOW();
		ELSE
			RAISE EXCEPTION 'Invalid job attempt update (%), cannot transition from % -> %', OLD.path, OLD.status, NEW.status;
		END IF;
	END IF;

	RETURN NEW;
END $$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS t_exp_attempt_add ON nimrod_job_attempts;
CREATE TRIGGER t_exp_attempt_add BEFORE INSERT OR UPDATE ON nimrod_job_attempts
	FOR EACH ROW EXECUTE PROCEDURE _exp_t_attempt_add();



DROP TYPE IF EXISTS nimrod_command_result_status CASCADE;
CREATE TYPE nimrod_command_result_status AS ENUM(
	'PRECONDITION_FAILURE',
	'SYSTEM_ERROR',
	'EXCEPTION',
	'ABORTED',
	'SUCCESS'
);

/*
** Command results, used for detailed tracking.
*/
DROP TABLE IF EXISTS nimrod_command_results CASCADE;
CREATE TABLE nimrod_command_results(
	id BIGSERIAL NOT NULL PRIMARY KEY,
	attempt_id BIGINT NOT NULL REFERENCES nimrod_job_attempts(id) ON DELETE CASCADE,
	status nimrod_command_result_status NOT NULL,
	command_index BIGINT NOT NULL,
	time REAL NOT NULL,
	retval INT NOT NULL DEFAULT -1,
	message TEXT NOT NULL DEFAULT '',
	error_code INT NOT NULL DEFAULT 0,
	stop BOOLEAN NOT NULL,
	command_id BIGINT NOT NULL REFERENCES nimrod_commands(id) ON DELETE CASCADE,
	UNIQUE(attempt_id, command_index)
);

CREATE OR REPLACE FUNCTION _exp_t_command_result_add() RETURNS TRIGGER AS $$
DECLARE
	_job_id BIGINT;
	_exp_id BIGINT;
	_task_id BIGINT;
BEGIN
	SELECT job_id INTO _job_id FROM nimrod_job_attempts WHERE id = NEW.attempt_id;
	SELECT exp_id INTO _exp_id FROM nimrod_jobs WHERE id = _job_id;
	SELECT id INTO _task_id FROM nimrod_tasks WHERE exp_id = _exp_id AND name = 'main'::nimrod_task_name;

	/* If NULL or negative command index, assume the next one. */
	IF NEW.command_index IS NULL OR NEW.command_index < 0 THEN
		SELECT COALESCE(MAX(command_index) + 1, 0) INTO NEW.command_index FROM nimrod_command_results WHERE attempt_id = NEW.attempt_id;
	END IF;

	/* Query the command id */
	SELECT id INTO NEW.command_id FROM nimrod_commands WHERE task_id = _task_id AND command_index = NEW.command_index;
	-- IF NEW.command_id IS NULL THEN
	-- 	RAISE EXCEPTION 'job_id = %, exp_id = %, task_id = %, command_index = %', _job_id, _exp_id, _task_id, NEW.command_index;
	-- END IF;
	RETURN NEW;
END
$$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS t_exp_command_result_add ON nimrod_command_results;
CREATE TRIGGER t_exp_command_result_add BEFORE INSERT ON nimrod_command_results
	FOR EACH ROW EXECUTE PROCEDURE _exp_t_command_result_add();

CREATE OR REPLACE FUNCTION add_command_result(
	_attempt_id BIGINT,
	_status nimrod_command_result_status,
	_command_index BIGINT,
	_time REAL,
	_retval INT,
	_message TEXT,
	_error_code INT,
	_stop BOOLEAN
) RETURNS nimrod_command_results AS $$
	INSERT INTO nimrod_command_results(attempt_id, status, command_index, time, retval, message, error_code, stop)
	VALUES(_attempt_id, _status, _command_index, _time, _retval, _message, _error_code, _stop)
	RETURNING *;
$$ LANGUAGE SQL VOLATILE;

-- These are "owned" by the command, but need to be looked-up by the job and attempts
DROP TABLE IF EXISTS nimrod_job_results;
CREATE TABLE nimrod_job_results(
	id			BIGSERIAL NOT NULL PRIMARY KEY,
	command_id	BIGINT NOT NULL REFERENCES nimrod_command_results(id) ON DELETE CASCADE,
	attempt_id	BIGINT REFERENCES nimrod_job_attempts(id) ON DELETE SET NULL,
	job_id		BIGINT REFERENCES nimrod_jobs(id) ON DELETE SET NULL,
	results		JSONB NOT NULL,
);

CREATE OR REPLACE FUNCTION add_job_result()
CREATE OR REPLACE FUNCTION create_job_attempt(_job_id BIGINT, _uuid UUID) RETURNS SETOF nimrod_job_attempts AS $$
	INSERT INTO nimrod_job_attempts(job_id, uuid)
	VALUES (_job_id, _uuid)
	RETURNING *;
$$ LANGUAGE SQL VOLATILE;

CREATE OR REPLACE FUNCTION start_job_attempt(_att_id BIGINT, _agent_uuid UUID) RETURNS SETOF nimrod_job_attempts AS $$
	UPDATE nimrod_job_attempts
	SET
		status = 'RUNNING'::nimrod_job_status,
		agent_uuid = _agent_uuid
	WHERE
		id = _att_id
	RETURNING *;
$$ LANGUAGE SQL VOLATILE;

CREATE OR REPLACE FUNCTION finish_job_attempt(_att_id BIGINT, _failed BOOLEAN) RETURNS SETOF nimrod_job_attempts AS $$
	UPDATE nimrod_job_attempts
	SET
		status = (
			SELECT CASE _failed
				WHEN FALSE THEN 'COMPLETED'::nimrod_job_status
				WHEN TRUE THEN 'FAILED'::nimrod_job_status
			END
		)
	WHERE
		id = _att_id
	RETURNING *;
$$ LANGUAGE SQL VOLATILE;

/*
** nimrod=# SELECT * FROM _exp_get_attempt_info(1);
**  status  | total_count | not_run | running | completed | failed
** ---------+-------------+---------+---------+-----------+--------
**  RUNNING |           3 |       0 |       2 |         0 |      1
*/
CREATE OR REPLACE FUNCTION _exp_get_attempt_info(_job_id BIGINT) RETURNS TABLE(status nimrod_job_status, total_count BIGINT, not_run BIGINT, running BIGINT, completed BIGINT, failed BIGINT) AS $$
	WITH counts AS (
		SELECT
			COUNT(1) AS total_count,
			COUNT(1) FILTER (WHERE status = 'NOT_RUN'::nimrod_job_status) AS not_run,
			COUNT(1) FILTER (WHERE status = 'RUNNING'::nimrod_job_status) AS running,
			COUNT(1) FILTER (WHERE status = 'COMPLETED'::nimrod_job_status) AS completed,
			COUNT(1) FILTER (WHERE status = 'FAILED'::nimrod_job_status) AS failed
		FROM
			nimrod_job_attempts AS att
		WHERE
			att.job_id = _job_id
	)
	SELECT
		CASE
			WHEN c.total_count = 0 OR c.total_count = c.not_run THEN 'NOT_RUN'::nimrod_job_status
			WHEN c.completed > 0 THEN 'COMPLETED'::nimrod_job_status
			WHEN c.failed > 0 AND c.completed = 0 AND c.running = 0 THEN 'FAILED'::nimrod_job_status
			WHEN c.running > 0 AND c.completed = 0 THEN 'RUNNING'::nimrod_job_status
		END,
		c.total_count,
		c.not_run,
		c.running,
		c.completed,
		c.failed
	FROM
		counts AS c
	;
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION get_job_status(_job_id BIGINT) RETURNS nimrod_job_status AS $$
	SELECT status FROM _exp_get_attempt_info(_job_id);
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION filter_job_attempts(_job_id BIGINT, _status nimrod_job_status[]) RETURNS SETOF nimrod_job_attempts AS $$
	SELECT
		*
	FROM
		nimrod_job_attempts
	WHERE
		job_id = _job_id AND
		status = ANY(COALESCE(_status, enum_range(NULL::nimrod_job_status)))
	;
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION filter_job_attempts_by_experiment(_exp_id BIGINT, _status nimrod_job_status[]) RETURNS SETOF nimrod_job_attempts AS $$
	SELECT
		att.*
	FROM
		nimrod_job_attempts AS att
	INNER JOIN
		nimrod_jobs AS j
		ON j.id = att.job_id
	WHERE
		j.exp_id = _exp_Id AND
		att.status = ANY(COALESCE(_status, enum_range(NULL::nimrod_job_status)))
	;
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION get_job_attempt(_att_id BIGINT) RETURNS SETOF nimrod_job_attempts AS $$
	SELECT * FROM nimrod_job_attempts WHERE id = _att_id;
$$ LANGUAGE SQL STABLE;

/*
** The nimrod_jobs table, except with three extra fields containg:
** - The derived state of the job.
** - An array of the variable names.
** - An array of the variable values.
*/
DROP VIEW IF EXISTS nimrod_full_jobs CASCADE;
CREATE VIEW nimrod_full_jobs AS
	SELECT
		j.*,
		get_job_status(j.id) AS status,
		(j.variables || jsonb_build_object('jobindex', j.job_index::TEXT, 'jobname', j.job_index::TEXT)) AS full_variables
	FROM
		nimrod_jobs AS j
;

CREATE OR REPLACE FUNCTION filter_jobs(_exp_id BIGINT, _status nimrod_job_status[], _start BIGINT, _limit BIGINT) RETURNS SETOF nimrod_full_jobs AS $$
	SELECT
		*
	FROM
		nimrod_full_jobs
	WHERE
		exp_id = _exp_id AND
		job_index >= COALESCE(_start, 0) AND
		status = ANY(COALESCE(_status, enum_range(NULL::nimrod_job_status)))
	ORDER BY job_index ASC
	LIMIT _limit;
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION get_jobs_by_id(_ids BIGINT[]) RETURNS SETOF nimrod_full_jobs AS $$
	SELECT * FROM nimrod_full_jobs WHERE id = ANY(_ids);
$$ LANGUAGE SQL STABLE;

/*
** This isn't actually used, it's too slow.
*/
CREATE OR REPLACE FUNCTION get_run_counts(_exp_id BIGINT) RETURNS TABLE(total_count BIGINT, not_run BIGINT, running BIGINT, completed BIGINT, failed BIGINT) AS $$
/* You can do this in a single query, but it's ungodly slow:
	SELECT
		COUNT(1) AS total_count,
		COUNT(1) FILTER (WHERE status = 'NOT_RUN'::nimrod_job_status) AS not_run,
		COUNT(1) FILTER (WHERE status = 'RUNNING'::nimrod_job_status) AS running,
		COUNT(1) FILTER (WHERE status = 'COMPLETED'::nimrod_job_status) AS completed,
		COUNT(1) FILTER (WHERE status = 'FAILED'::nimrod_job_status) AS failed
	FROM
		nimrod_full_jobs AS j
	WHERE
		j.exp_id = _exp_id
	;
*/
DECLARE
	_not_run BIGINT;
	_running BIGINT;
	_completed BIGINT;
	_failed BIGINT;
	_s RECORD;
BEGIN
	_not_run := 0;
	_running := 0;
	_completed := 0;
	_failed := 0;

	FOR _s IN
		SELECT status FROM nimrod_full_jobs WHERE exp_id = _exp_id
	LOOP
		IF _s.status = 'NOT_RUN' THEN
			_not_run := _not_run + 1;
		ELSIF _s._status = 'RUNNING' THEN
			_running := _running + 1;
		ELSIF _s._status = 'COMPLETED' THEN
			_completed := _completed + 1;
		ELSIF _s._status = 'FAILED' THEN
			_failed := _failed + 1;
		END IF;
	END LOOP;

	RETURN QUERY
		SELECT
			_not_run + _running + _completed + _failed,
			_not_run,
			_running,
			_completed,
			_failed
	;
END
$$ LANGUAGE 'plpgsql' STABLE;

/*
** Is the provided token valid for modification of the given run's storage.
** If the token is an attempt-token, return the id of the attempt.
** If the token is an experiment token, return 0.
** If the token is invalid, return -1.
*/
CREATE OR REPLACE FUNCTION is_token_valid_for_experiment_storage(_exp_id BIGINT, _token TEXT) RETURNS BIGINT AS $$
	SELECT r.* FROM (
		SELECT
			0 AS id
		FROM
			nimrod_experiments
		WHERE
			id = _exp_id AND
			file_token = _token
		UNION ALL
		SELECT
			att.id AS id
		FROM
			nimrod_job_attempts AS att,
			nimrod_jobs AS j
		WHERE
			att.job_id = j.id AND
			j.exp_id = _exp_id AND
			att.token = _token
		UNION ALL
		SELECT -1 AS id
	) AS r
	ORDER BY id DESC
	LIMIT 1
	;
$$ LANGUAGE SQL STABLE;

-- Validate job key names, will throw if invalid
CREATE OR REPLACE FUNCTION validate_jobs_json(_exp_id BIGINT, _jobs JSONB) RETURNS VOID AS $$
DECLARE
	count_ BIGINT;
BEGIN
	CREATE TEMPORARY TABLE tmp(
		_keys TEXT[] UNIQUE
	) ON COMMIT DROP;

	-- Get all the unique sets of keys
	INSERT INTO tmp(_keys)
	SELECT DISTINCT
		jk.keys
	FROM
		jsonb_array_elements(_jobs) AS ja
	LEFT JOIN LATERAL(
		SELECT array_agg(k) AS keys
		FROM jsonb_object_keys(ja) AS k
	) AS jk
	ON TRUE
	;

	SELECT COUNT(*) INTO count_ FROM tmp;
	IF count_ > 1 THEN
		RAISE EXCEPTION 'Mismatched job variables %', _jobs;
	END IF;

	SELECT
		(
			(SELECT array_agg(x ORDER BY x) FROM unnest(e.variables) x)
			=
			(SELECT array_agg(x ORDER BY x) FROM unnest(tmp._keys) x)
		)::INT INTO count_
	FROM
		tmp,
		nimrod_experiments AS e
	WHERE
		e.id = _exp_id;

	IF count_ = 0 THEN
		RAISE EXCEPTION 'Mismatched job variables';
	END IF;

-- This isn't actually needed, as it'll be caught above, Keeping for reference
-- 	SELECT COUNT(c.*) INTO count_
-- 	FROM (
-- 		SELECT DISTINCT unnest(_keys) FROM xxx
-- 		INTERSECT
-- 		SELECT name FROM nimrod_reserved_variables
-- 	) AS c;
--
-- 	IF count_ != 0 THEN
-- 		RAISE EXCEPTION 'Jobs cannot have variables with reserved names';
-- 	END IF;
	DROP TABLE tmp;
END $$ LANGUAGE plpgsql;

/*
** Add a list of jobs to an experiment.
** _jobs is expected to be like '[{"x": "1", "y": "2"}, {"x": "2", "y": "3"}]'
*/
CREATE OR REPLACE FUNCTION add_multiple_jobs_internal(_exp_id BIGINT, _jobs JSONB) RETURNS SETOF BIGINT AS $$
	SELECT validate_jobs_json(_exp_id, _jobs);

	INSERT INTO nimrod_jobs(exp_id, job_index, variables)
	SELECT
		_exp_id,
		COALESCE(ji.job_index, 0) + j.index,
		j.job
	FROM
		jsonb_array_elements(_jobs) WITH ORDINALITY AS j(job, index)
	LEFT OUTER JOIN -- NB: There may be no jobs. There will be max one row, so this is fine.
		(SELECT job_index FROM nimrod_jobs WHERE exp_id = _exp_id ORDER BY job_index DESC LIMIT 1) AS ji
	ON TRUE
	RETURNING id
	;
$$ LANGUAGE SQL VOLATILE;

CREATE OR REPLACE FUNCTION add_multiple_jobs(_exp_id BIGINT, _jobs JSONB) RETURNS SETOF nimrod_full_jobs AS $$
DECLARE
	_ids BIGINT[];
BEGIN
	SELECT array_agg(j) INTO _ids FROM add_multiple_jobs_internal(_exp_id, _jobs) AS j;
	RETURN QUERY SELECT * FROM nimrod_full_jobs WHERE id IN (SELECT unnest(_ids));
END
$$ LANGUAGE 'plpgsql' VOLATILE;