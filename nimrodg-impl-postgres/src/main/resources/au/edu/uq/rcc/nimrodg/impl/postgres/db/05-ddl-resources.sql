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
/* This is a port of the SQLite logic */

DROP TYPE IF EXISTS nimrod_agent_state CASCADE;
CREATE TYPE nimrod_agent_state AS ENUM('WAITING_FOR_HELLO', 'READY', 'BUSY', 'SHUTDOWN');

DROP TYPE IF EXISTS nimrod_agent_shutdown_reason CASCADE;
CREATE TYPE nimrod_agent_shutdown_reason AS ENUM('HostSignal', 'Requested');

DROP TABLE IF EXISTS nimrod_resource_types CASCADE;
CREATE TABLE nimrod_resource_types(
	id						BIGSERIAL NOT NULL PRIMARY KEY,
	implementation_class	TEXT NOT NULL,
	name					TEXT NOT NULL UNIQUE
);

DROP TABLE IF EXISTS nimrod_resources CASCADE;
CREATE TABLE nimrod_resources (
	id						BIGSERIAL NOT NULL PRIMARY KEY,
	name					nimrod_identifier NOT NULL UNIQUE,
	type_id					BIGINT NOT NULL REFERENCES nimrod_resource_types(id) ON DELETE RESTRICT,
	config					JSONB NOT NULL,
	/* I'm not using nimrod_uri to keep SELECTion and INSERTion code simple. */
	amqp_uri				TEXT,
	amqp_cert_path			TEXT,
	amqp_no_verify_peer		BOOLEAN,
	amqp_no_verify_host		BOOLEAN,
	tx_uri					TEXT,
	tx_cert_path			TEXT,
	tx_no_verify_peer		BOOLEAN,
	tx_no_verify_host		BOOLEAN
);

DROP VIEW IF EXISTS nimrod_full_resources CASCADE;
CREATE VIEW nimrod_full_resources AS(
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
		LEFT JOIN get_config() AS cfg ON TRUE
		LEFT JOIN nimrod_resource_types AS t ON r.type_id = t.id
);

DROP TABLE IF EXISTS nimrod_resource_assignments CASCADE;
CREATE TABLE nimrod_resource_assignments(
	id						BIGSERIAL NOT NULL PRIMARY KEY,
	exp_id					BIGINT NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	resource_id				BIGINT NOT NULL REFERENCES nimrod_resources(id) ON DELETE CASCADE,
	tx_uri					TEXT,
	tx_cert_path			TEXT,
	tx_no_verify_peer		BOOLEAN,
	tx_no_verify_host		BOOLEAN,
	UNIQUE(exp_id, resource_id)
);

DROP VIEW IF EXISTS nimrod_full_resource_assignments;
CREATE VIEW nimrod_full_resource_assignments AS
SELECT
	a.id,
	a.exp_id,
	a.resource_id,
	COALESCE(a.tx_uri, r.tx_uri, cfg.tx_uri) AS tx_uri,
	COALESCE(a.tx_cert_path, r.tx_cert_path, cfg.tx_cert_path) AS tx_cert_path,
	COALESCE(a.tx_no_verify_peer, r.tx_no_verify_peer, cfg.tx_no_verify_peer) AS tx_no_verify_peer,
	COALESCE(a.tx_no_verify_host, r.tx_no_verify_host, cfg.tx_no_verify_host) AS tx_no_verify_host,
	CASE WHEN a.tx_uri IS NULL THEN e.work_dir ELSE NULL END AS work_dir
FROM
	nimrod_resource_assignments AS a INNER JOIN
	nimrod_resources AS r ON a.resource_id = r.id LEFT JOIN
	nimrod_experiments AS e ON a.exp_id = e.id LEFT JOIN
	nimrod_config AS cfg ON cfg.id = 1
;

DROP TABLE IF EXISTS nimrod_resource_capabilities CASCADE;
CREATE TABLE nimrod_resource_capabilities(
	id						BIGSERIAL NOT NULL PRIMARY KEY,
	exp_id					BIGINT NOT NULL REFERENCES nimrod_experiments(id) ON DELETE CASCADE,
	resource_id				BIGINT NOT NULL REFERENCES nimrod_resources(id) ON DELETE CASCADE,
	UNIQUE(exp_id, resource_id)
);

DROP TABLE IF EXISTS nimrod_resource_agents CASCADE;
CREATE TABLE nimrod_resource_agents(
	id						BIGSERIAL NOT NULL PRIMARY KEY,
	state					nimrod_agent_state NOT NULL,
	queue					TEXT CHECK((state != 'WAITING_FOR_HELLO'::nimrod_agent_state AND state != 'SHUTDOWN') != (queue IS NULL)),
	agent_uuid				UUID NOT NULL UNIQUE,
	shutdown_signal			INTEGER NOT NULL,
	shutdown_reason			nimrod_agent_shutdown_reason NOT NULL,
	/* From here, none of this is actually state. */
	created					TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
	connected_at				TIMESTAMP WITH TIME ZONE CHECK(connected_at >= created),
	last_heard_from			TIMESTAMP WITH TIME ZONE CHECK(last_heard_from >= created),
	expiry_time				TIMESTAMP WITH TIME ZONE CHECK(expiry_time >= created),
	expired_at				TIMESTAMP WITH TIME ZONE DEFAULT NULL CHECK(expired_at >= created),
	expired					BOOLEAN NOT NULL DEFAULT FALSE,
	location					BIGINT REFERENCES nimrod_resources(id) ON DELETE CASCADE,
	actuator_data			JSONB
);
CREATE INDEX ON nimrod_resource_agents(location) WHERE location IS NOT NULL;

/*
** If the agent's expired, disallow changes.
** If the agent's state is setting to SHUTDOWN, mark it as expired.
*/
CREATE OR REPLACE FUNCTION _res_t_agent_expire_on_shutdown() RETURNS TRIGGER AS $$
BEGIN
	IF OLD.expired = TRUE THEN
		RAISE EXCEPTION 'Cannot update expired agent';
	END IF;

	IF NEW.state = 'SHUTDOWN'::nimrod_agent_state THEN
		NEW.expired = TRUE;
	END IF;

	IF NEW.expired = TRUE THEN
		NEW.expired_at = NOW();
	END IF;

	/* Sometimes there's a few ms drift between the sever and db. */
	IF NEW.last_heard_from < NEW.created THEN
		NEW.last_heard_from = NEW.created;
	END IF;

	RETURN NEW;
END $$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS t_res_agent_expire_on_shutdown ON nimrod_resource_agents;
CREATE TRIGGER t_res_agent_expire_on_shutdown BEFORE UPDATE ON nimrod_resource_agents
	FOR EACH ROW EXECUTE PROCEDURE _res_t_agent_expire_on_shutdown();

/*
** Delete any capabilities if their experiment has been unassigned.
*/
CREATE OR REPLACE FUNCTION _res_t_assignment_remove_checks() RETURNS TRIGGER AS $$
BEGIN
	DELETE FROM nimrod_resource_capabilities
	WHERE
		exp_id IN (SELECT id FROM nimrod_experiments WHERE exp_id = OLD.exp_id) AND
		resource_id = OLD.resource_id
	;
	RETURN NULL;
END $$ LANGUAGE 'plpgsql';

DROP TRIGGER IF EXISTS t_res_assignment_remove_checks ON nimrod_resource_assignments;
CREATE TRIGGER t_res_assignment_remove_checks AFTER DELETE ON nimrod_resource_assignments
	FOR EACH ROW EXECUTE PROCEDURE _res_t_assignment_remove_checks();

/*
** ACTUAL FUNCTIONS
*/

CREATE OR REPLACE FUNCTION get_resource_type_info(_type TEXT) RETURNS SETOF nimrod_resource_types AS $$
	SELECT * FROM nimrod_resource_types WHERE name = _type;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_resource_type_info() RETURNS SETOF nimrod_resource_types AS $$
	SELECT * FROM nimrod_resource_types;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_resource(_name nimrod_identifier) RETURNS SETOF nimrod_full_resources AS $$
	SELECT * FROM nimrod_full_resources WHERE name = _name;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_resource(_name TEXT, _typename TEXT, _config JSONB, _amqp_uri nimrod_uri, _tx_uri nimrod_uri) RETURNS SETOF nimrod_full_resources AS $$
DECLARE
	_type_id BIGINT;
	_resource_id BIGINT;
BEGIN
	SELECT id INTO _type_id FROM nimrod_resource_types WHERE name = _typename;
	if _type_id IS NULL THEN
		RAISE EXCEPTION 'Invalid type %', _typename;
	END IF;

	INSERT INTO nimrod_resources(
		name,
		type_id,
		config,
		amqp_uri,
		amqp_cert_path,
		amqp_no_verify_peer,
		amqp_no_verify_host,
		tx_uri,
		tx_cert_path,
		tx_no_verify_peer,
		tx_no_verify_host
	)
	VALUES(
		_name,
		_type_id,
		_config,
		_amqp_uri.uri,
		_amqp_uri.cert_path,
		_amqp_uri.no_verify_peer,
		_amqp_uri.no_verify_host,
		_tx_uri.uri,
		_tx_uri.cert_path,
		_tx_uri.no_verify_peer,
		_tx_uri.no_verify_host
	) RETURNING id INTO _resource_id;

	RETURN QUERY SELECT * FROM nimrod_full_resources WHERE id = _resource_id;
END
$$ LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION delete_resource(_id BIGINT) RETURNS VOID AS $$
	DELETE FROM nimrod_resources WHERE id = _id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_resources() RETURNS SETOF nimrod_full_resources AS $$
	SELECT * FROM nimrod_full_resources;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_assigned_resources(_exp_id BIGINT) RETURNS SETOF nimrod_full_resources AS $$
	SELECT s.*
	FROM
		nimrod_resource_assignments AS a,
		nimrod_full_resources AS s
	WHERE
		a.resource_id = s.id AND
		a.exp_id = _exp_id
	;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION assign_resource(_res_id BIGINT, _exp_id BIGINT, _tx_uri nimrod_uri) RETURNS SETOF nimrod_resource_assignments AS $$
	INSERT INTO nimrod_resource_assignments(
		exp_id,
		resource_id,
		tx_uri,
		tx_cert_path,
		tx_no_verify_peer,
		tx_no_verify_host
	)
	VALUES(
		_exp_id,
		_res_id,
		_tx_uri.uri,
		_tx_uri.cert_path,
		_tx_uri.no_verify_peer,
		_tx_uri.no_verify_host
	)
	ON CONFLICT DO NOTHING
	RETURNING *;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION unassign_resource(_res_id BIGINT, _exp_id BIGINT) RETURNS VOID AS $$
	DELETE FROM nimrod_resource_assignments WHERE resource_id = _res_id AND exp_id = _exp_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_assignment_status(_res_id BIGINT, _exp_id BIGINT) RETURNS nimrod_full_resource_assignments AS $$
	SELECT * FROM nimrod_full_resource_assignments WHERE resource_id = _res_id AND exp_id = _exp_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION is_resource_capable(_res_id BIGINT, _exp_id BIGINT) RETURNS BOOLEAN AS $$
	SELECT COUNT(id) > 0 FROM nimrod_resource_capabilities WHERE resource_id = _res_id AND exp_id = _exp_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_resource_caps(_res_id BIGINT, _exp_id BIGINT) RETURNS VOID AS $$
	INSERT INTO nimrod_resource_capabilities(exp_id, resource_id)
	VALUES (_exp_id, _res_id)
	ON CONFLICT DO NOTHING;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION remove_resource_caps(_res_id BIGINT, _exp_id BIGINT) RETURNS VOID AS $$
	DELETE FROM nimrod_resource_capabilities WHERE resource_id = _res_id AND exp_id = _exp_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_agent_information(_uuid UUID) RETURNS SETOF nimrod_resource_agents AS $$
	SELECT * FROM nimrod_resource_agents WHERE agent_uuid = _uuid;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_agent_resource(_uuid UUID) RETURNS SETOF nimrod_full_resources AS $$
	SELECT * FROM nimrod_full_resources WHERE id = (
		SELECT location FROM nimrod_resource_agents WHERE agent_uuid = _uuid
	);
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_agents_on_resource(_res_id BIGINT) RETURNS SETOF nimrod_resource_agents AS $$
	SELECT * FROM nimrod_resource_agents WHERE location = _res_id AND expired = FALSE;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_agent(_state nimrod_agent_state, _queue TEXT, _uuid UUID, _shutdown_signal INTEGER, _shutdown_reason nimrod_agent_shutdown_reason, _expiry_time TIMESTAMP WITH TIME ZONE, _location BIGINT, _actuator_data JSONB) RETURNS nimrod_resource_agents AS $$
	INSERT INTO nimrod_resource_agents(
		state,
		queue,
		agent_uuid,
		shutdown_signal,
		shutdown_reason,
		expiry_time,
		location,
		actuator_data
	)
	VALUES(
		_state,
		_queue,
		_uuid,
		_shutdown_signal,
		_shutdown_reason,
		_expiry_time,
		_location,
		_actuator_data
	)
	RETURNING *;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_agent(_uuid UUID, _state nimrod_agent_state, _queue TEXT, _signal INTEGER, _reason nimrod_agent_shutdown_reason, _connected_at TIMESTAMP WITH TIME ZONE, _last_heard_from TIMESTAMP WITH TIME ZONE, _expiry_time TIMESTAMP WITH TIME ZONE, _expired BOOLEAN) RETURNS SETOF nimrod_resource_agents AS $$
	UPDATE nimrod_resource_agents
	SET
		state = _state,
		queue = _queue,
		shutdown_signal = _signal,
		shutdown_reason = _reason,
		connected_at = _connected_at,
		last_heard_from = _last_heard_from,
		expiry_time = _expiry_time,
		expired = _expired
	WHERE
		agent_uuid = _uuid
	RETURNING *;
$$ LANGUAGE SQL;
