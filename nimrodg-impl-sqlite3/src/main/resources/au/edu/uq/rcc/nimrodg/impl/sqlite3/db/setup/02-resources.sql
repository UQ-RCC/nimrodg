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
DROP TABLE IF EXISTS nimrod_resource_types;
CREATE TABLE nimrod_resource_types(
    id                      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    implementation_class    TEXT    NOT NULL,
    name                    TEXT    NOT NULL UNIQUE
);

DROP TABLE IF EXISTS nimrod_resources;
CREATE TABLE nimrod_resources(
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name                TEXT    NOT NULL UNIQUE,
    type_id	            INTEGER NOT NULL REFERENCES nimrod_resource_types(id) ON DELETE RESTRICT,
    config              TEXT    NOT NULL,
    amqp_uri            TEXT,
    amqp_cert_path      TEXT,
    amqp_no_verify_peer BOOLEAN,
    amqp_no_verify_host BOOLEAN,
    tx_uri              TEXT,
    tx_cert_path        TEXT,
    tx_no_verify_peer   BOOLEAN,
    tx_no_verify_host   BOOLEAN
);

DROP VIEW IF EXISTS nimrod_full_resources;
CREATE VIEW nimrod_full_resources AS
SELECT
    r.id,
    r.name,
    r.type_id,
    r.config,
    COALESCE(r.amqp_uri, cfg.amqp_uri) AS amqp_uri,
    COALESCE(r.amqp_cert_path, cfg.amqp_cert_path) AS amqp_cert_path,
    COALESCE(r.amqp_no_verify_peer, cfg.amqp_no_verify_peer) AS amqp_no_verify_peer,
    COALESCE(r.amqp_no_verify_host, cfg.amqp_no_verify_host) AS amqp_no_verify_host,
    COALESCE(r.tx_uri, cfg.tx_uri) AS tx_uri,
    COALESCE(r.tx_cert_path, cfg.tx_cert_path) AS tx_cert_path,
    COALESCE(r.tx_no_verify_peer, cfg.tx_no_verify_peer) AS tx_no_verify_peer,
    COALESCE(r.tx_no_verify_host, cfg.tx_no_verify_host) AS tx_no_verify_host,
    t.name AS type_name,
    t.implementation_class AS type_class
FROM
    nimrod_resources AS r
    LEFT JOIN nimrod_config AS cfg ON TRUE
    LEFT JOIN nimrod_resource_types AS t ON r.type_id = t.id
WHERE
    cfg.id = 1
;

DROP TABLE IF EXISTS nimrod_resource_assignments;
CREATE TABLE nimrod_resource_assignments(
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    exp_id              INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
    resource_id         INTEGER NOT NULL REFERENCES nimrod_resources(id) ON DELETE RESTRICT,
    tx_uri              TEXT,
    tx_cert_path        TEXT,
    tx_no_verify_peer   BOOLEAN,
    tx_no_verify_host   BOOLEAN,
    UNIQUE(exp_id, resource_id)
);

DROP VIEW IF EXISTS nimrod_assigned_resources;
CREATE VIEW nimrod_assigned_resources AS
SELECT
    s.*,
    a.exp_id
FROM
    nimrod_resource_assignments AS a
    LEFT JOIN nimrod_full_resources AS s ON a.resource_id = s.id
;

DROP VIEW IF EXISTS nimrod_full_resource_assignments;
CREATE VIEW nimrod_full_resource_assignments AS
SELECT
    a.id,
    a.exp_id,
    a.resource_id,
    COALESCE(a.tx_uri, r.tx_uri, cfg.tx_uri) AS tx_uri,
    COALESCE(a.tx_cert_path, r.tx_cert_path, cfg.tx_cert_path) AS tx_cert_path,
    COALESCE(a.tx_no_verify_peer, r.tx_no_verify_peer, cfg.tx_no_verify_peer) AS tx_no_verify_peer,
    COALESCE(a.tx_no_verify_host, r.tx_no_verify_host, cfg.tx_no_verify_host) AS tx_no_verify_host
FROM
    nimrod_resource_assignments AS a INNER JOIN
    nimrod_resources AS r ON a.resource_id = r.id LEFT JOIN
    nimrod_config AS cfg ON cfg.id = 1
;

DROP TABLE IF EXISTS nimrod_resource_capabilities;
CREATE TABLE nimrod_resource_capabilities(
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    exp_id      INTEGER NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
    resource_id INTEGER NOT NULL REFERENCES nimrod_resources(id) ON DELETE CASCADE,
    UNIQUE(exp_id, resource_id)
);

DROP TRIGGER IF EXISTS t_delete_caps_on_unassign;
CREATE TRIGGER t_delete_caps_on_unassign AFTER DELETE ON nimrod_resource_assignments FOR EACH ROW
BEGIN
    DELETE FROM nimrod_resource_capabilities WHERE exp_id = OLD.exp_id AND resource_id = OLD.resource_id;
END;

DROP TABLE IF EXISTS nimrod_resource_agents;
CREATE TABLE nimrod_resource_agents(
    id              INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    state           TEXT    NOT NULL CHECK(state IN ('WAITING_FOR_HELLO', 'READY', 'BUSY', 'SHUTDOWN')),
    queue           TEXT    CHECK((state IN ('WAITING_FOR_HELLO', 'SHUTDOWN') AND queue IS NULL) OR (queue IS NOT NULL)),
    agent_uuid      UUID    NOT NULL UNIQUE,
    shutdown_signal INTEGER NOT NULL,
    shutdown_reason TEXT    NOT NULL CHECK(shutdown_reason IN ('HostSignal', 'Requested')),
    -- From here, none of this is actually state.
    created         INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    connected_at    INTEGER DEFAULT NULL CHECK(connected_at >= created),
    last_heard_from INTEGER DEFAULT NULL CHECK(last_heard_from >= created),
    expiry_time     INTEGER DEFAULT NULL CHECK(expiry_time >= created),
    expired_at      INTEGER DEFAULT NULL CHECK(expired_at >= created),
    expired         BOOLEAN NOT NULL DEFAULT FALSE,
    secret_key      TEXT    NOT NULL DEFAULT (LOWER(HEX(RANDOMBLOB(16)))),
    location        INTEGER REFERENCES nimrod_resources(id) ON DELETE CASCADE,
    actuator_data   TEXT
);
DROP INDEX IF EXISTS i_agent_location;
CREATE INDEX i_agent_location ON nimrod_resource_agents(location) WHERE location IS NOT NULL;

DROP TRIGGER IF EXISTS t_agent_expire_on_shutdown;
CREATE TRIGGER t_agent_expire_on_shutdown AFTER UPDATE ON nimrod_resource_agents FOR EACH ROW WHEN OLD.state != 'SHUTDOWN' AND NEW.state = 'SHUTDOWN'
BEGIN
    UPDATE nimrod_resource_agents SET expired_at = strftime('%s', 'now'), expired = TRUE WHERE id = OLD.id;
END;

DROP TRIGGER IF EXISTS t_agent_connect;
CREATE TRIGGER t_agent_connect AFTER UPDATE ON nimrod_resource_agents FOR EACH ROW WHEN OLD.state = 'WAITING_FOR_HELLO' AND NEW.state = 'READY'
BEGIN
    UPDATE nimrod_resource_agents SET connected_at = strftime('%s', 'now') WHERE id = OLD.id;
END;
