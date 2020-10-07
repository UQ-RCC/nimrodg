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

-- Dump a substitution to JSON.
CREATE OR REPLACE FUNCTION _gt_getsub(_sub_id BIGINT) RETURNS JSONB AS $$
	SELECT jsonb_build_object(
		'name', v.name,
		'start', s.start_index,
		'end', s.end_index,
		'relative', s.relative_start
	)
	FROM
		nimrod_substitutions AS s,
		nimrod_variables AS v
	WHERE
		s.id = _sub_id AND
		v.id = s.variable_id
	;
$$ LANGUAGE SQL STABLE;

-- Dump an argument to JSON
CREATE OR REPLACE FUNCTION _gt_getarg(_arg_id BIGINT) RETURNS JSONB AS $$
	SELECT jsonb_build_object(
		'text', ca.arg_text,
		'substitutions', COALESCE(sub.s, '[]'::JSONB)
	)
	FROM
		nimrod_command_arguments AS ca,
		(SELECT jsonb_agg(_gt_getsub(id)) AS s FROM nimrod_substitutions WHERE arg_id = _arg_id) AS sub
	WHERE
		ca.id = _arg_id;
$$ LANGUAGE SQL STABLE;

-- Dump an onerror command to denormalised JSON
CREATE OR REPLACE FUNCTION _gt_getcmd_onerror(_data JSONB[]) RETURNS JSONB AS $$
	SELECT jsonb_build_object(
		'type', 'onerror',
		'action', _data[1]->>'text'
	);
$$ LANGUAGE SQL IMMUTABLE;

-- Dump a redirect command to denormalized JSON
CREATE OR REPLACE FUNCTION _gt_getcmd_redirect(_data JSONB[]) RETURNS JSONB AS $$
	SELECT jsonb_build_object(
		'type', 'redirect',
		'stream', _data[1]->>'text',
		'append', (_data[2]->>'text')::BOOLEAN,
		'file', _gt_getarg((_data[3]->>'id')::BIGINT)
	);
$$ LANGUAGE SQL STABLE;

-- Dump a copy command to denormalised JSON
CREATE OR REPLACE FUNCTION _gt_getcmd_copy(_data JSONB[]) RETURNS JSONB AS $$
	SELECT jsonb_build_object(
		'type', 'copy',
		'source_context', _data[1]->>'text',
		'source_path', _gt_getarg((_data[2]->>'id')::BIGINT),
		'destination_context', _data[3]->>'text',
		'destination_path', _gt_getarg((_data[4]->>'id')::BIGINT)
	);
$$ LANGUAGE SQL STABLE;

-- Dump an exec command to denormalised JSON
CREATE OR REPLACE FUNCTION _gt_getcmd_exec(_data JSONB[]) RETURNS JSONB AS $$
	WITH args AS(
		SELECT (unnest(_data[3:array_upper(_data, 1)])->>'id')::BIGINT AS id
	)
	SELECT jsonb_build_object(
		'type', 'exec',
		'search_path', (_data[1]->>'text')::BOOLEAN,
		'program', _data[2]->>'text',
		'arguments', array_agg(_gt_getarg(args.id))
	)
	FROM args;
$$ LANGUAGE SQL STABLE;

-- Dump a command to denormalised JSON
CREATE OR REPLACE FUNCTION _gt_getcmd(_cmd_id BIGINT) RETURNS JSONB AS $$
	WITH args AS(
		SELECT
			array_agg(jsonb_build_object('id', id, 'text', arg_text) ORDER BY arg_index) AS data,
			COUNT(id) AS count
		FROM
			nimrod_command_arguments
		WHERE
			command_id = _cmd_id
	)
	SELECT
		CASE
			WHEN cmd.type = 'onerror' THEN _gt_getcmd_onerror(a.data)
			WHEN cmd.type = 'redirect' THEN _gt_getcmd_redirect(a.data)
			WHEN cmd.type = 'copy' THEN _gt_getcmd_copy(a.data)
			WHEN cmd.type = 'exec' THEN _gt_getcmd_exec(a.data)
		END
	FROM
		nimrod_commands AS cmd,
		args AS a
	WHERE
		cmd.id = _cmd_id;
	;
$$ LANGUAGE SQL STABLE;

-- Dump the commands of a task to a JSON array
CREATE OR REPLACE FUNCTION _gt_export_task(_task_id BIGINT) RETURNS JSONB AS $$
	 SELECT jsonb_agg(_gt_getcmd(id)) FROM nimrod_commands WHERE task_id = _task_id;
$$ LANGUAGE SQL STABLE;

-- Dump the tasks of an experiment to JSON
CREATE OR REPLACE FUNCTION export_tasks(_exp_id BIGINT) RETURNS JSONB AS $$
	SELECT
		CASE
			WHEN COUNT(t.id) > 0 THEN jsonb_object_agg(t.name, _gt_export_task(t.id))
			ELSE '{}'::JSONB
		END
	FROM
		nimrod_tasks AS t
	WHERE
		exp_id = _exp_id
	;
$$ LANGUAGE SQL STABLE;

DROP VIEW IF EXISTS nimrod_full_experiments CASCADE;
CREATE VIEW nimrod_full_experiments AS(
	SELECT
		e.*,
		array_to_json(e.variables) AS vars_json,
		export_tasks(e.id) AS tasks_json
	FROM
		nimrod_experiments AS e
);
