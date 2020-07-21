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
DROP TABLE IF EXISTS nimrod_config;
CREATE TABLE nimrod_config(
	id					INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	work_dir			TEXT NOT NULL,
	store_dir			TEXT NOT NULL,
	amqp_uri			TEXT NOT NULL,
	amqp_cert_path		TEXT NOT NULL DEFAULT '',
	amqp_no_verify_peer	BOOLEAN NOT NULL DEFAULT FALSE,
	amqp_no_verify_host	BOOLEAN NOT NULL DEFAULT FALSE,
	amqp_routing_key	TEXT NOT NULL,
	tx_uri				TEXT NOT NULL,
	tx_cert_path		TEXT NOT NULL DEFAULT '',
	tx_no_verify_peer	BOOLEAN NOT NULL DEFAULT FALSE,
	tx_no_verify_host	BOOLEAN NOT NULL DEFAULT FALSE
);


DROP TABLE IF EXISTS nimrod_kv_config;
CREATE TABLE nimrod_kv_config(
	id		INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	key		TEXT NOT NULL UNIQUE,
	value	TEXT NOT NULL
);


/*
** Agent definitions.
*/
DROP TABLE IF EXISTS nimrod_agents;
CREATE TABLE nimrod_agents(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	platform_string TEXT NOT NULL UNIQUE,
	path TEXT NOT NULL
);

/*
** POSIX uname (system,machine) -> agent mappings
*/
DROP TABLE IF EXISTS nimrod_agent_mappings;
CREATE TABLE nimrod_agent_mappings(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	system TEXT NOT NULL,
	machine TEXT NOT NULL,
	agent_id BIGINT NOT NULL REFERENCES nimrod_agents(id) ON DELETE CASCADE,
	UNIQUE(system, machine)
);

DROP VIEW IF EXISTS nimrod_agentinfo_by_platform;
CREATE VIEW nimrod_agentinfo_by_platform AS
SELECT
	a.id,
	a.platform_string,
	m.system,
	m.machine,
	a.path
FROM
	nimrod_agents AS a LEFT JOIN
	nimrod_agent_mappings AS m ON a.id = m.agent_id
;

DROP VIEW IF EXISTS nimrod_agentinfo_by_posix;
CREATE VIEW nimrod_agentinfo_by_posix AS
SELECT DISTINCT
	a2.*
FROM
	nimrod_agent_mappings AS a1 INNER JOIN
	nimrod_agentinfo_by_platform a2 ON a1.agent_id = a2.id
;

DROP TABLE IF EXISTS nimrod_reserved_variables;
CREATE TABLE nimrod_reserved_variables(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	name TEXT NOT NULL UNIQUE,
	description TEXT NOT NULL
);

INSERT INTO nimrod_reserved_variables(name, description)
VALUES
	('jobindex', 'The index of the current job. Starts at 1.'),
	('jobname', 'Same as ''jobindex''. Provided for compatibility purposes only.')
;

DROP TABLE IF EXISTS nimrod_schema_version;
CREATE TABLE nimrod_schema_version(
    major INTEGER NOT NULL CHECK(major >= 0),
    minor INTEGER NOT NULL CHECK(minor >= 0),
    patch INTEGER NOT NULL CHECK(patch >= 0),
    CHECK(ROWID == 1)
);

INSERT INTO nimrod_schema_version(major, minor, patch)
VALUES (2, 1, 0);

--
-- SQLite doesn't have stored procedures, so abuse a trigger to compare a schema version.
--
-- UPDATE nimrod_schema_version SET major = 1, minor = 0, patch = 0;
-- - Will RAISE if the schema version isn't 1.0.0
-- - To actually change the version, DELETE and INSERT a new row.
--
DROP TRIGGER IF EXISTS t_update_version;
CREATE TRIGGER t_update_version BEFORE UPDATE ON nimrod_schema_version FOR EACH ROW
BEGIN
    WITH yy AS(
        SELECT CASE
            WHEN NEW.major > OLD.major THEN 1
            WHEN NEW.major < OLD.major THEN -1
            ELSE CASE
                WHEN NEW.minor > OLD.minor THEN 1
                WHEN NEW.minor < OLD.minor THEN -1
                ELSE CASE
                    WHEN NEW.patch > OLD.patch THEN 1
                    WHEN NEW.patch < OLD.patch THEN -1
                    ELSE 0
                END
            END
        END AS c
    )
    SELECT CASE
        WHEN c < 0 THEN RAISE(ABORT, 'Schema too new, nothing to do')
        WHEN c > 0 THEN RAISE(ABORT, 'Schema too old, refusing upgrade')
        WHEN c = 0 THEN RAISE(IGNORE)
    END FROM yy;
END;
