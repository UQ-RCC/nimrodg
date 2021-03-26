.bail on

BEGIN TRANSACTION;

--
-- Check our schema is the correct version.
--
UPDATE nimrod_schema_version SET major = 1, minor = 0, patch = 0;

--
-- Drop the file_token column.
--
CREATE TABLE nimrod_experiments2(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	name TEXT NOT NULL UNIQUE,
	state TEXT NOT NULL CHECK(state IN ('STOPPED', 'STARTED', 'PERSISTENT')) DEFAULT 'STOPPED',
	work_dir TEXT NOT NULL UNIQUE,
	created INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
	path TEXT NOT NULL UNIQUE
);

INSERT INTO nimrod_experiments2
SELECT id, name, state, work_dir, created, path FROM nimrod_experiments;

--
-- To swap tables out from underneath a view
--
PRAGMA legacy_alter_table = ON;
DROP TABLE nimrod_experiments;
ALTER TABLE nimrod_experiments2 RENAME TO nimrod_experiments;
PRAGMA legacy_alter_table = OFF;

--
-- Drop the token column
--
CREATE TABLE nimrod_job_attempts2(
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

INSERT INTO nimrod_job_attempts2
SELECT id, job_id, uuid, status, creation_time, start_time, finish_time, path, agent_uuid FROM nimrod_job_attempts;

DROP TABLE nimrod_job_attempts;
ALTER TABLE nimrod_job_attempts2 RENAME TO nimrod_job_attempts;

--
-- All changes done, now actually update the version.
--
DELETE FROM nimrod_schema_version;
INSERT INTO nimrod_schema_version(major, minor, patch) VALUES(2, 0, 0);

COMMIT;
