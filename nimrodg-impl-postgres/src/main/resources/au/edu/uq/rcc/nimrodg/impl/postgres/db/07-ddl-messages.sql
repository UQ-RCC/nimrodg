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
DROP TYPE IF EXISTS nimrod_master_message_class CASCADE;
CREATE TYPE nimrod_master_message_class AS ENUM('config', 'job');

DROP TYPE IF EXISTS nimrod_message_operation CASCADE;
CREATE TYPE nimrod_message_operation AS ENUM('DELETE', 'INSERT', 'UPDATE');

--
-- Payload Helpers
--
CREATE OR REPLACE FUNCTION _msg_build_payload_config_init(_cfg nimrod_kv_config) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'key', _cfg.key,
        'old', NULL,
        'value', _cfg.value
    );
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION _msg_build_payload_config_deinit(_cfg nimrod_kv_config) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'key', _cfg.key,
        'old', _cfg.value,
        'value', NULL
    );
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION _msg_build_payload_config_update(_old nimrod_kv_config, _new nimrod_kv_config) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'key', _old.key,
        'old', _old.value,
        'value', _new.value
    );
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION _msg_build_payload_job(_job nimrod_jobs) RETURNS JSONB AS $$
    SELECT jsonb_build_object(
        'id', _job.id,
        'exp_id', _job.exp_id,
        'job_index', _job.job_index,
        'created', (SELECT EXTRACT(EPOCH FROM _job.created))::BIGINT
    );
$$ LANGUAGE SQL;

DROP TABLE IF EXISTS nimrod_master_message_storage CASCADE;
CREATE TABLE nimrod_master_message_storage(
    id        BIGSERIAL NOT NULL PRIMARY KEY,
    operation nimrod_message_operation NOT NULL,
    class     nimrod_master_message_class NOT NULL,
    ts        TIMESTAMP WITH TIME ZONE NOT NULL,
    payload   JSONB NOT NULL
);

CREATE OR REPLACE FUNCTION poll_master_messages() RETURNS SETOF nimrod_master_message_storage AS $$
    DELETE FROM nimrod_master_message_storage RETURNING *;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_master_message(op nimrod_message_operation, class nimrod_master_message_class, payload JSONB) RETURNS VOID AS $$
    INSERT INTO nimrod_master_message_storage(operation, class, ts, payload)
    VALUES(op, class, NOW(), payload);
$$ LANGUAGE SQL;

--
-- Trigger to catch config INSERT/DELETE/UPDATE on nimrod_kv_config
--
CREATE OR REPLACE FUNCTION _msg_t_config() RETURNS TRIGGER AS $$
DECLARE
    payload JSONB;
BEGIN
    IF TG_OP = 'DELETE' THEN
        payload := _msg_build_payload_config_deinit(OLD);
    ELSIF TG_OP = 'INSERT' THEN
        payload := _msg_build_payload_config_init(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        payload := _msg_build_payload_config_update(OLD, NEW);
    ELSE
        RAISE EXCEPTION 'Operation % not valid for configuration change', TG_OP;
    END IF;
    --RAISE INFO '%', payload;

    PERFORM add_master_message(TG_OP::nimrod_message_operation, 'config'::nimrod_master_message_class, payload);
    RETURN NULL;
END $$ LANGUAGE 'plpgsql';
DROP TRIGGER IF EXISTS t_msg_config ON nimrod_kv_config;
CREATE TRIGGER t_msg_config AFTER INSERT OR UPDATE OR DELETE ON nimrod_kv_config FOR EACH ROW EXECUTE PROCEDURE _msg_t_config();

--
-- Trigger to catch job INSERT on nimrod_jobs
--
CREATE OR REPLACE FUNCTION _msg_t_job() RETURNS TRIGGER AS $$
DECLARE
    payload JSONB;
    expstate nimrod_experiment_state;
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Only add the message if the experiments's active.
        SELECT state INTO expstate FROM nimrod_experiments WHERE id = NEW.exp_id;
        IF expstate != 'STOPPED' THEN
            payload := _msg_build_payload_job(NEW);
            PERFORM add_master_message(TG_OP::nimrod_message_operation, 'job'::nimrod_master_message_class, payload);
        END IF;
    ELSE
        RAISE EXCEPTION 'Operation % not valid for job', TG_OP;
    END IF;

    RETURN NULL;
END $$ LANGUAGE 'plpgsql';
DROP TRIGGER IF EXISTS t_msg_job ON nimrod_jobs;
CREATE TRIGGER t_msg_job AFTER INSERT ON nimrod_jobs FOR EACH ROW EXECUTE PROCEDURE _msg_t_job();
