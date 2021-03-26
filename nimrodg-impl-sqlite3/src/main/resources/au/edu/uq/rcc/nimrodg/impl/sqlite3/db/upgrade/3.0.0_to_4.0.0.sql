.bail on

BEGIN TRANSACTION;

--
-- Check our schema is the correct version.
--
UPDATE nimrod_schema_version SET major = 3, minor = 0, patch = 0;

--
-- Remove the path field
--
CREATE TABLE nimrod_job_attempts2(
    id              INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    job_id          INTEGER NOT NULL REFERENCES nimrod_jobs(id) ON DELETE CASCADE,
    uuid            UUID    NOT NULL UNIQUE,
    status          TEXT    NOT NULL DEFAULT 'NOT_RUN' CHECK(status IN ('NOT_RUN', 'RUNNING', 'COMPLETED', 'FAILED')),
    creation_time   INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    start_time      INTEGER DEFAULT NULL,
    finish_time     INTEGER DEFAULT NULL,
    -- Weak reference to the agent UUID. The agent may or may not exist
    agent_uuid      UUID/* REFERENCES nimrod_master_agents(id) */,
    CHECK(finish_time >= start_time)
);

INSERT INTO nimrod_job_attempts2
SELECT id, job_id, uuid, status, creation_time, start_time, finish_time, agent_uuid FROM nimrod_job_attempts;

DROP TABLE nimrod_job_attempts;
ALTER TABLE nimrod_job_attempts2 RENAME TO nimrod_job_attempts;


--
-- Remove the path field
--
CREATE TABLE nimrod_jobs2(
    id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    exp_id    INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
    job_index BIGINT  NOT NULL CHECK(job_index > 0),
    created   INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    variables TEXT    NOT NULL,
    UNIQUE(exp_id, job_index)
);
INSERT INTO nimrod_jobs2
SELECT id, exp_id, job_index, created, variables FROM nimrod_jobs;

DROP TABLE nimrod_jobs;
ALTER TABLE nimrod_jobs2 RENAME TO nimrod_jobs;


--
-- Remove the path field
--
CREATE TABLE nimrod_experiments2(
    id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name      TEXT    NOT NULL UNIQUE,
    state     TEXT    NOT NULL CHECK(state IN ('STOPPED', 'STARTED', 'PERSISTENT')) DEFAULT 'STOPPED',
    work_dir  TEXT    NOT NULL UNIQUE,
    created   INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
);
INSERT INTO nimrod_experiments2
SELECT id, name, state, work_dir, created FROM nimrod_experiments;

DROP TABLE nimrod_experiments;
ALTER TABLE nimrod_experiments2 RENAME TO nimrod_experiments;

--
-- All changes done, now actually update the version.
--
DELETE FROM nimrod_schema_version;
INSERT INTO nimrod_schema_version(major, minor, patch) VALUES(4, 0, 0);

COMMIT;
