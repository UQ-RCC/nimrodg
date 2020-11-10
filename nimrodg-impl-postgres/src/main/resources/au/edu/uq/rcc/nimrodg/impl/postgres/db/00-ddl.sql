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
DROP DOMAIN IF EXISTS nimrod_identifier CASCADE;
CREATE DOMAIN nimrod_identifier AS TEXT CHECK (VALUE ~ '^[a-zA-Z0-9_]+$');
DROP DOMAIN IF EXISTS nimrod_variable_identifier CASCADE;
CREATE DOMAIN nimrod_variable_identifier AS TEXT CHECK (VALUE ~ '^[a-zA-Z_][a-zA-Z0-9_]*$');
DROP DOMAIN IF EXISTS nimrod_kv_config_key CASCADE;
CREATE DOMAIN nimrod_kv_config_key AS TEXT CHECK (VALUE ~ '^[a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*');

--
-- Create a random hex token of a specified length.
--
CREATE OR REPLACE FUNCTION _generate_random_token(_length INT) RETURNS TEXT AS $$
    SELECT string_agg(to_hex(width_bucket(random(), 0, 1, 16)-1), '') FROM generate_series(1, _length);
$$ LANGUAGE SQL VOLATILE;

DROP TYPE IF EXISTS nimrod_uri CASCADE;
CREATE TYPE nimrod_uri AS
(
    uri            TEXT,
    -- Certificate path. If URI is ssh, this would double as a private key path.
    cert_path      TEXT,
    no_verify_peer BOOLEAN,
    no_verify_host BOOLEAN
);

-- Make a nimrod_uri structure.
CREATE OR REPLACE FUNCTION make_uri(_uri TEXT, _cert_path TEXT, _no_verify_peer BOOLEAN, _no_verify_host BOOLEAN) RETURNS nimrod_uri AS $$
    SELECT (NULLIF(_uri, ''), NULLIF(_cert_path, ''), _no_verify_peer, _no_verify_host)::nimrod_uri;
$$ LANGUAGE SQL IMMUTABLE;

DROP TABLE IF EXISTS nimrod_config CASCADE;
CREATE TABLE nimrod_config(
    id                  BIGSERIAL NOT NULL PRIMARY KEY,
    -- Absolute path for Nimrod's working directory.
    work_dir            TEXT NOT NULL,
    store_dir           TEXT NOT NULL,
    -- URI configuration for the message queue.
    amqp_uri            TEXT NOT NULL,
    amqp_cert_path      TEXT NOT NULL DEFAULT '',
    amqp_no_verify_peer BOOLEAN NOT NULL DEFAULT FALSE,
    amqp_no_verify_host BOOLEAN NOT NULL DEFAULT FALSE,
    amqp_routing_key    TEXT NOT NULL,
    -- URI configuration for the file server.
    tx_uri              TEXT NOT NULL,
    tx_cert_path        TEXT NOT NULL DEFAULT '',
    tx_no_verify_peer   BOOLEAN NOT NULL DEFAULT FALSE,
    tx_no_verify_host   BOOLEAN NOT NULL DEFAULT FALSE
);

--
-- Insert/Update configuration. Any field set to NULL will use the old value.
-- Only the row with id = 1 is updated. It is created if it doesn't exist.
--
CREATE OR REPLACE FUNCTION update_config(_work_dir TEXT, _store_dir TEXT, _amqp_uri nimrod_uri, _amqp_routing_key TEXT, _tx_uri nimrod_uri) RETURNS nimrod_config AS $$
    INSERT INTO nimrod_config(
        id, work_dir, store_dir,
        amqp_uri, amqp_cert_path, amqp_no_verify_peer, amqp_no_verify_host,
        amqp_routing_key,
        tx_uri, tx_cert_path, tx_no_verify_peer, tx_no_verify_host
    )
    VALUES(
        1,
        _work_dir,
        _store_dir,
        _amqp_uri.uri,
        COALESCE(_amqp_uri.cert_path, ''),
        COALESCE(_amqp_uri.no_verify_peer, FALSE),
        COALESCE(_amqp_uri.no_verify_host, FALSE),
        _amqp_routing_key,
        _tx_uri.uri,
        COALESCE(_tx_uri.cert_path, ''),
        COALESCE(_tx_uri.no_verify_peer, FALSE),
        COALESCE(_tx_uri.no_verify_host, FALSE)
    )
    ON CONFLICT(id) DO UPDATE
        SET
            work_dir            = COALESCE(EXCLUDED.work_dir, nimrod_config.work_dir),
            store_dir           = COALESCE(EXCLUDED.store_dir, nimrod_config.store_dir),
            amqp_uri            = COALESCE(EXCLUDED.amqp_uri, nimrod_config.amqp_uri),
            amqp_cert_path      = COALESCE(EXCLUDED.amqp_cert_path, nimrod_config.amqp_cert_path, ''),
            amqp_no_verify_peer = COALESCE(EXCLUDED.amqp_no_verify_peer, nimrod_config.amqp_no_verify_peer, FALSE),
            amqp_no_verify_host = COALESCE(EXCLUDED.amqp_no_verify_host, nimrod_config.amqp_no_verify_host, FALSE),
            amqp_routing_key    = COALESCE(EXCLUDED.amqp_routing_key, nimrod_config.amqp_routing_key),
            tx_uri              = COALESCE(EXCLUDED.tx_uri, nimrod_config.tx_uri),
            tx_cert_path        = COALESCE(EXCLUDED.tx_cert_path, nimrod_config.tx_cert_path, ''),
            tx_no_verify_peer   = COALESCE(EXCLUDED.tx_no_verify_peer, nimrod_config.tx_no_verify_peer, FALSE),
            tx_no_verify_host   = COALESCE(EXCLUDED.tx_no_verify_host, nimrod_config.tx_no_verify_host, FALSE)
    RETURNING *;
$$ LANGUAGE SQL;

--
-- Get the global configuration.
-- This will throw if setup hasn't been run.
--
CREATE OR REPLACE FUNCTION get_config() RETURNS nimrod_config AS $$
DECLARE
    cfg nimrod_config;
BEGIN
    SELECT * INTO cfg FROM nimrod_config WHERE id = 1;

    IF cfg.id IS NULL THEN
        RAISE EXCEPTION 'No configuration, please run setup.';
    END IF;

    RETURN cfg;
END $$ LANGUAGE 'plpgsql';

--
-- Key/Value Configuration properties
--
DROP TABLE IF EXISTS nimrod_kv_config CASCADE;
CREATE TABLE nimrod_kv_config(
    id    BIGSERIAL NOT NULL PRIMARY KEY,
    key   nimrod_kv_config_key NOT NULL UNIQUE,
    value TEXT NOT NULL
);

--
-- TEXT get_property(TEXT key);
--
CREATE OR REPLACE FUNCTION get_property(_key nimrod_kv_config_key) RETURNS TEXT AS $$
    SELECT value FROM nimrod_kv_config WHERE key = _key;
$$ LANGUAGE SQL;

--
-- TEXT set_property(TEXT key, TEXT value);
--
CREATE OR REPLACE FUNCTION set_property(_key nimrod_kv_config_key, _value TEXT) RETURNS TEXT AS $$
DECLARE
    old_value TEXT;
BEGIN
    SELECT value INTO old_value FROM nimrod_kv_config WHERE key = _key;

    IF _value IS NULL OR _value = '' THEN
        DELETE FROM nimrod_kv_config WHERE key = _key;
    ELSE
        INSERT INTO nimrod_kv_config(key, value)
        VALUES
            (_key, _value)
        ON CONFLICT(key) DO UPDATE
            SET value = excluded.value;
    END IF;

    RETURN old_value;
END $$ LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION get_properties() RETURNS SETOF nimrod_kv_config AS $$
    SELECT * FROM nimrod_kv_config;
$$ LANGUAGE SQL;

--
-- Agent definitions.
--
DROP TABLE IF EXISTS nimrod_agents CASCADE;
CREATE TABLE nimrod_agents(
    id              BIGSERIAL NOT NULL PRIMARY KEY,
    platform_string TEXT NOT NULL UNIQUE,
    path            TEXT NOT NULL
);

--
-- POSIX uname (system,machine) -> agent mappings
--
DROP TABLE IF EXISTS nimrod_agent_mappings CASCADE;
CREATE TABLE nimrod_agent_mappings(
    id          BIGSERIAL NOT NULL PRIMARY KEY,
    system      TEXT NOT NULL,
    machine     TEXT NOT NULL,
    agent_id    BIGINT NOT NULL REFERENCES nimrod_agents(id) ON DELETE CASCADE,
    UNIQUE(system, machine)
);

DROP VIEW IF EXISTS nimrod_agent_info CASCADE;
CREATE VIEW nimrod_agent_info AS(
    SELECT
        a.*,
        COALESCE(map.mappings, '{}') AS mappings
    FROM
        nimrod_agents AS a LEFT JOIN LATERAL(
            SELECT
                array_agg(ARRAY[m.system, m.machine]) AS mappings
            FROM
                nimrod_agent_mappings AS m
            WHERE
                m.agent_id = a.id
        ) map ON true
);

DROP VIEW IF EXISTS nimrod_mapped_agents CASCADE;
CREATE VIEW nimrod_mapped_agents AS(
    SELECT
        a.id AS id,
        m.id AS mapping_id,
        m.system AS system,
        m.machine AS machine,
        a.platform_string AS platform_string,
        a.path AS path,
        a.mappings AS mappings
    FROM
        nimrod_agent_mappings AS m LEFT JOIN
        nimrod_agent_info AS a
    ON
        a.id = m.agent_id
);

CREATE OR REPLACE FUNCTION lookup_agent(_platstring TEXT) RETURNS SETOF nimrod_mapped_agents AS $$
    SELECT * FROM nimrod_mapped_agents WHERE platform_string = _platstring;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION lookup_agent(_system TEXT, _machine TEXT) RETURNS SETOF nimrod_mapped_agents AS $$
    SELECT
        a.*
    FROM
        nimrod_mapped_agents AS a LEFT JOIN
        nimrod_agent_mappings AS m
    ON
        m.agent_id = a.id
    WHERE
        m.system = _system AND
        m.machine = _machine
;
$$ LANGUAGE SQL;

DROP TABLE IF EXISTS nimrod_reserved_variables CASCADE;
CREATE TABLE nimrod_reserved_variables(
    id          BIGSERIAL NOT NULL PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL
);

INSERT INTO nimrod_reserved_variables(name, description)
VALUES
    ('jobindex', 'The index of the current job. Starts at 1.'),
    ('jobname', 'Same as ''jobindex''. Provided for compatibility purposes only.')
;

CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
    SELECT 5, 0, 0;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION is_schema_compatible(_major INTEGER, _minor INTEGER, _patch INTEGER) RETURNS BOOLEAN AS $$
    SELECT _major = v.major AND _minor <= v.minor AND _patch <= v.patch FROM get_schema_version() AS v;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION compare_schema_version(_major INTEGER, _minor INTEGER, _patch INTEGER) RETURNS INTEGER AS $$
    SELECT CASE
        WHEN _major > v.major THEN 1
        WHEN _major < v.major THEN -1
        ELSE CASE
            WHEN _minor > v.minor THEN 1
            WHEN _minor < v.minor THEN -1
            ELSE CASE
                WHEN _patch > v.patch THEN 1
                WHEN _patch < v.patch THEN -1
                ELSE 0
            END
        END
    END FROM
        get_schema_version() AS v
    ;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION require_schema_compatible(major INTEGER, minor INTEGER, patch INTEGER) RETURNS VOID AS $$
    DECLARE
        _major INTEGER;
        _minor INTEGER;
        _patch INTEGER;
    BEGIN
        IF is_schema_compatible(major, minor, patch) THEN
            RETURN;
        END IF;

        SELECT v.major, v.minor, v.patch INTO _major, _minor, _patch FROM get_schema_version() AS v;

        RAISE EXCEPTION 'Incompatible schema (minimum = %.%.%, current = %.%.%)',
            major, minor, patch,
            _major, _minor, _patch
        ;
    END
$$ LANGUAGE 'plpgsql' IMMUTABLE;