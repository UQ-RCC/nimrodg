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
DROP TABLE IF EXISTS nimrod_experiments;
CREATE TABLE nimrod_experiments(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	name TEXT NOT NULL UNIQUE,
	state TEXT NOT NULL CHECK(state IN ('STOPPED', 'STARTED', 'PERSISTENT')) DEFAULT 'STOPPED',
	work_dir TEXT NOT NULL UNIQUE,
	created INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
	path TEXT NOT NULL UNIQUE
);

DROP TABLE IF EXISTS nimrod_jobs;
CREATE TABLE nimrod_jobs(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	exp_id INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	job_index BIGINT NOT NULL CHECK(job_index > 0),
	created INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
	variables TEXT NOT NULL,
	path TEXT NOT NULL UNIQUE,
	UNIQUE(exp_id, job_index)
);

DROP TABLE IF EXISTS nimrod_variables;
CREATE TABLE nimrod_variables(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	exp_id INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	name TEXT NOT NULL,
	UNIQUE(exp_id, name)
);

DROP VIEW IF EXISTS nimrod_user_variables;
CREATE VIEW nimrod_user_variables AS
	SELECT
		v.*
	FROM
		nimrod_variables AS v LEFT JOIN
		nimrod_reserved_variables AS rv ON v.name = rv.name
	WHERE
		rv.name IS NULL
;

DROP TABLE IF EXISTS nimrod_tasks;
CREATE TABLE nimrod_tasks(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	exp_id INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	name NOT NULL CHECK(name IN ('nodestart', 'main')),
	UNIQUE(exp_id, name)
);

DROP TABLE IF EXISTS nimrod_commands;
CREATE TABLE nimrod_commands(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	command_index INTEGER NOT NULL CHECK(command_index >= 0),
	task_id INTEGER NOT NULL REFERENCES nimrod_tasks(id) ON DELETE CASCADE,
	type TEXT NOT NULL CHECK(type in ('onerror', 'redirect', 'copy', 'exec')),
	UNIQUE(command_index, task_id)
);

DROP TABLE IF EXISTS nimrod_command_arguments;
CREATE TABLE nimrod_command_arguments(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	command_id INTEGER NOT NULL REFERENCES nimrod_commands(id) ON DELETE CASCADE,
	arg_index BIGINT NOT NULL,
	arg_text TEXT NOT NULL,
	UNIQUE(arg_index, command_id)
);

DROP TABLE IF EXISTS nimrod_substitutions;
CREATE TABLE nimrod_substitutions(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	arg_id INTEGER NOT NULL REFERENCES nimrod_command_arguments(id) ON DELETE CASCADE,
	variable_id INTEGER NOT NULL REFERENCES nimrod_variables(id) ON DELETE CASCADE,
	start_index INT NOT NULL CHECK(start_index >= 0),
	end_index INT NOT NULL CHECK(end_index > start_index),
	relative_start INT NOT NULL CHECK(relative_start >= 0)
);

DROP VIEW IF EXISTS nimrod_full_substitutions;
CREATE VIEW nimrod_full_substitutions AS
SELECT
	s.*,
	v.name
FROM
	nimrod_substitutions AS s LEFT JOIN
	nimrod_variables AS v
ON
	s.variable_id = v.id
;

/*
** All the possible attempts at running a job.
** TODO: Add invariant conditions to this
*/
DROP TABLE IF EXISTS nimrod_job_attempts;
CREATE TABLE nimrod_job_attempts(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	/* Job id */
	job_id INTEGER NOT NULL REFERENCES nimrod_jobs(id) ON DELETE CASCADE,
	uuid UUID NOT NULL UNIQUE,
	status TEXT NOT NULL DEFAULT 'NOT_RUN' CHECK(status IN ('NOT_RUN', 'RUNNING', 'COMPLETED', 'FAILED')),
	creation_time INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
	start_time INTEGER DEFAULT NULL,
	finish_time INTEGER DEFAULT NULL,
	path TEXT NOT NULL UNIQUE,
	/* Weak reference to the agent UUID. The agent may or may not exist */
	agent_uuid UUID/* REFERENCES nimrod_master_agents(id) */,
	CHECK(finish_time >= start_time)
);

DROP TRIGGER IF EXISTS t_set_attempt_start_time;
CREATE TRIGGER t_set_attempt_start_time AFTER UPDATE ON nimrod_job_attempts FOR EACH ROW WHEN OLD.status = 'NOT_RUN' AND NEW.status = 'RUNNING'
BEGIN
	UPDATE nimrod_job_attempts SET start_time = strftime('%s', 'now') WHERE id = NEW.id;
END;

DROP TRIGGER IF EXISTS t_set_attempt_finish_time;
CREATE TRIGGER t_set_attempt_finish_time AFTER UPDATE ON nimrod_job_attempts FOR EACH ROW WHEN OLD.status = 'RUNNING' AND NEW.status IN ('COMPLETED', 'FAILED')
BEGIN
	UPDATE nimrod_job_attempts SET finish_time = strftime('%s', 'now') WHERE id = NEW.id;
END;

DROP TRIGGER IF EXISTS t_set_attempt_both_times;
CREATE TRIGGER t_set_attempt_both_times AFTER UPDATE ON nimrod_job_attempts FOR EACH ROW WHEN OLD.status = 'NOT_RUN' AND NEW.status IN ('COMPLETED', 'FAILED')
BEGIN
	UPDATE nimrod_job_attempts SET start_time = strftime('%s', 'now'), finish_time = strftime('%s', 'now') WHERE id = NEW.id;
END;

DROP TABLE IF EXISTS nimrod_command_results;
CREATE TABLE nimrod_command_results(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	attempt_id INTEGER NOT NULL REFERENCES nimrod_job_attempts(id) ON DELETE CASCADE,
	status TEXT NOT NULL CHECK(status IN ('PRECONDITION_FAILURE', 'SYSTEM_ERROR', 'EXCEPTION', 'ABORTED', 'FAILED', 'SUCCESS')),
	command_index INTEGER NOT NULL,
	time REAL NOT NULL,
	retval INT NOT NULL DEFAULT -1,
	message TEXT NOT NULL DEFAULT '',
	error_code INT NOT NULL DEFAULT 0,
	stop BOOLEAN NOT NULL,
	command_id INTEGER NOT NULL REFERENCES nimrod_commands(id) ON DELETE CASCADE,
	UNIQUE(attempt_id, command_index)
);