--
-- Nimrod Portal Backend
-- https://github.com/UQ-RCC/nimrod-portal-backend
--
-- SPDX-License-Identifier: Apache-2.0
-- Copyright (c) 2021 The University of Queensland
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

-- Don't uncomment these unless you really need to.
--DROP DATABASE IF EXISTS nimrod_portal;
--CREATE DATABASE nimrod_portal;


DROP DOMAIN IF EXISTS public.nimrod_identifier CASCADE;
CREATE DOMAIN public.nimrod_identifier AS TEXT CHECK (VALUE ~ '^[a-zA-Z0-9_]+$');



CREATE TABLE IF NOT EXISTS public.portal_users(
    id                BIGSERIAL NOT NULL PRIMARY KEY,
    username        NAME NOT NULL UNIQUE,
    -- NB: Not actually being used as a hash, so this is fine
    -- Also have to keep these as plaintext to regen the configuration files
    pg_password        TEXT NOT NULL DEFAULT(MD5(random()::text) || MD5(random()::text)),
    amqp_password    TEXT NOT NULL DEFAULT(MD5(random()::text) || MD5(random()::text))
);

CREATE OR REPLACE VIEW public.portal_user_status AS
SELECT
    pu.*,
    (
        -- Check for the existence of the user's nimrod_config table to see if
        -- they need to be initialised.
        SELECT
            CASE COALESCE(r::TEXT, '') WHEN '' THEN FALSE ELSE TRUE END
        FROM
            to_regclass(pu.username || '.nimrod_config') AS r
    ) AS initialised
FROM
    public.portal_users AS pu
;


-- User's need to be able to see their own stuff
CREATE OR REPLACE VIEW public.current_portal_user AS
SELECT
    *
FROM
   public.portal_user_status
WHERE
    username = current_user
;
GRANT SELECT ON public.current_portal_user TO PUBLIC;

CREATE OR REPLACE FUNCTION public.portal_fix_user(_username NAME) RETURNS VOID AS $$
BEGIN
    -- Allow the user to see all data
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO %I', _username, _username);

    -- Allow the user to use the CLI (and by extension the Master)
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_full_experiments               TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_experiments                    TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_experiments_id_seq             TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,DELETE          ON %I.nimrod_master_message_storage         TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_jobs                           TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_jobs_id_seq                    TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_commands                       TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_commands_id_seq                TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_command_arguments              TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_command_arguments_id_seq       TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_substitutions                  TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_substitutions_id_seq           TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_tasks                          TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_tasks_id_seq                   TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_variables                      TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_variables_id_seq               TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_resources                      TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_resources_id_seq               TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,UPDATE,INSERT,DELETE   ON %I.nimrod_resource_assignments           TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_resource_assignments_id_seq    TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE   ON %I.nimrod_job_attempts                   TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_job_attempts_id_seq            TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,INSERT,DELETE          ON %I.nimrod_resource_capabilities          TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_resource_capabilities_id_seq   TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,UPDATE,INSERT,DELETE   ON %I.nimrod_resource_agents                TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_resource_agents_id_seq         TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,UPDATE,INSERT,DELETE   ON %I.nimrod_command_results                TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_command_results_id_seq         TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,UPDATE,INSERT,DELETE   ON %I.nimrod_kv_config                      TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_kv_config_id_seq               TO %I', _username, _username);
    EXECUTE format('GRANT SELECT,UPDATE,INSERT,DELETE   ON %I.nimrod_master_message_storage         TO %I', _username, _username);
    EXECUTE format('GRANT USAGE                         ON %I.nimrod_master_message_storage_id_seq  TO %I', _username, _username);
END $$ LANGUAGE 'plpgsql';

DROP FUNCTION IF EXISTS public.portal_set_user_debug(_username NAME);

CREATE OR REPLACE FUNCTION public.portal_create_user(_username NAME) RETURNS SETOF public.portal_user_status AS $$
DECLARE
    _user public.portal_users;
BEGIN
    SELECT * INTO _user FROM public.portal_users WHERE username = _username;

    IF _user.id IS NULL THEN
        INSERT INTO public.portal_users(username) VALUES(_username)
        RETURNING * INTO _user;
    END IF;

    IF NOT EXISTS(SELECT * FROM pg_roles WHERE rolname = _username) THEN
        EXECUTE format('CREATE ROLE %I WITH ENCRYPTED PASSWORD %L LOGIN', _username, _user.pg_password);
    ELSE
        EXECUTE format('ALTER ROLE %I WITH ENCRYPTED PASSWORD %L LOGIN', _username, _user.pg_password);
    END IF;

    -- Allow us to access the user's tables.
    EXECUTE format('GRANT %I TO CURRENT_USER', _username);

    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', _username);

    -- Allow the user to see their schema
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', _username, _username);

    RETURN QUERY SELECT * FROM public.portal_user_status WHERE id = _user.id;
END
$$ LANGUAGE 'plpgsql';

CREATE TABLE IF NOT EXISTS public.portal_planfiles(
    id          BIGSERIAL NOT NULL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES public.portal_users(id) ON DELETE CASCADE,
    exp_name    public.nimrod_identifier NOT NULL,
    planfile    TEXT NOT NULL,
    UNIQUE(user_id, exp_name)
);