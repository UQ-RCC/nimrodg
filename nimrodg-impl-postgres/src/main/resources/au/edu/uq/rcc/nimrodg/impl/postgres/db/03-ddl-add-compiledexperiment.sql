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

CREATE OR REPLACE FUNCTION _acr_add_command_argument(_exp_id BIGINT, _command_id BIGINT, _arg_index BIGINT, _command JSONB) RETURNS VOID AS $$
DECLARE
	arg_id BIGINT;
	sub JSONB;
	var_id BIGINT;
BEGIN
	INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text)
	VALUES(_command_id, _arg_index, _command->>'text')
	RETURNING id INTO arg_id;

	FOR sub IN SELECT * FROM jsonb_array_elements(_command->'substitutions')
	LOOP
		SELECT id INTO var_id FROM nimrod_variables WHERE exp_id = _exp_id AND name = sub->>'name';
		IF var_id IS NULL THEN
			RAISE EXCEPTION 'Invalid variable reference ''%'' in substitution', sub->>'name';
		END IF;

		INSERT INTO nimrod_substitutions(arg_id, variable_id, start_index, end_index, relative_start)
		VALUES(
			arg_id,
			var_id,
			(sub->>'start')::BIGINT,
			(sub->>'end')::BIGINT,
			(sub->>'relative')::BIGINT
		);
		--RAISE NOTICE '%', sub;
	END LOOP;
END $$ LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION _acr_add_task_commands(_exp_id BIGINT, _task_id BIGINT, _commands JSONB) RETURNS VOID AS $$
DECLARE
	cmd JSONB;
	arg JSONB;
	i BIGINT;
	command_id BIGINT;
	command_type nimrod_command_type;
	j BIGINT;
BEGIN
	i := 0;
	FOR cmd IN SELECT * FROM jsonb_array_elements(_commands)
	LOOP
		/* Add the command entry */
		INSERT INTO nimrod_commands(command_index, task_id, type)
		VALUES(i, _task_id, (cmd->>'type')::nimrod_command_type)
		RETURNING id, type INTO command_id, command_type;
		i := i + 1;

		/* Now add the arguments */
		IF command_type = 'onerror'::nimrod_command_type THEN
			INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text)
			VALUES(command_id, 0, cmd->>'action');
		ELSIF command_type = 'redirect'::nimrod_command_type THEN
			INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text)
			VALUES
				(command_id, 0, cmd->>'stream'),
				(command_id, 1, cmd->>'append')
				;
			PERFORM _acr_add_command_argument(_exp_id, command_id, 2, cmd->'file');
		ELSIF command_type = 'copy'::nimrod_command_type THEN
			INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text)
			VALUES
				(command_id, 0, cmd->>'source_context'),
				(command_id, 2, cmd->>'destination_context')
				;
			PERFORM _acr_add_command_argument(_exp_id, command_id, 1, cmd->'source_path');
			PERFORM _acr_add_command_argument(_exp_id, command_id, 3, cmd->'destination_path');
		ELSIF command_type = 'exec'::nimrod_command_type THEN
			INSERT INTO nimrod_command_arguments(command_id, arg_index, arg_text)
			VALUES
				(command_id, 0, (cmd->>'search_path')::BOOLEAN::TEXT),
				(command_id, 1, cmd->>'program')
				;

			j := 2;
			FOR arg IN SELECT * FROM jsonb_array_elements(cmd->'arguments')
			LOOP
				PERFORM _acr_add_command_argument(_exp_id, command_id, j, arg);
				j := j + 1;
			END LOOP;
		ELSE
			RAISE EXCEPTION 'Unknown command type %', command_type;
		END IF;
	END LOOP;
END $$ LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION add_compiled_experiment(_name TEXT, _work_dir TEXT, _file_token TEXT, _exp JSONB) RETURNS nimrod_full_experiments AS $$
DECLARE
	count_ BIGINT;

	vars TEXT[];
	rvars TEXT[];
	eexp_id BIGINT;
	exp_row nimrod_full_experiments;
BEGIN
	-- Validate "variables"
	SELECT
		array_agg(v.*) INTO vars
	FROM
		jsonb_array_elements_text(_exp->'variables') AS v
	;

	-- Check for duplicate variable names.
	SELECT
		COUNT(a.*) INTO count_
	FROM
		(SELECT DISTINCT * FROM unnest(vars)) AS a
	;

	IF count_ != array_length(vars, 1) THEN
		RAISE EXCEPTION 'Duplicate variable names';
	END IF;

	-- Check for reserved names.
	SELECT
		COUNT(a.*) INTO count_
	FROM (
		SELECT * FROM unnest(vars)
		INTERSECT
		SELECT name FROM nimrod_reserved_variables
	) AS a
	;

    IF count_ != 0 THEN
		RAISE EXCEPTION 'Experiment cannot have variables with reserved names.';
	END IF;


	-- Validate "results"
	SELECT
		COALESCE(array_agg(v.*), ARRAY[]::TEXT[]) INTO rvars
	FROM
		jsonb_array_elements_text(_exp->'results') AS v
	;

	SELECT
		COUNT(a.*) INTO count_
	FROM
		(SELECT DISTINCT * FROM unnest(rvars)) AS a
	;

	IF count_ != array_length(rvars, 1) THEN
		RAISE EXCEPTION 'Duplicate result names';
	END IF;

	-- Ensure we have a file token
	IF _file_token IS NULL THEN
		_file_token := _generate_random_token(32);
	END IF;

	-- Ensure work_dir is a directory.
	IF _work_dir NOT LIKE '%/' THEN
		_work_dir := _work_dir || '/';
	END IF;

	-- Add the experiment
	INSERT INTO nimrod_full_experiments(name, work_dir, file_token, variables, results, path)
	VALUES(_name, _work_dir, _file_token, vars, rvars, _name)
	RETURNING id INTO eexp_id;

	-- Add the variables
	INSERT INTO nimrod_variables(exp_id, name)
	SELECT eexp_id, unnest(vars);

	-- Add the implicit variables
	INSERT INTO nimrod_variables(exp_id, name)
	SELECT eexp_id, rv.name FROM nimrod_reserved_variables AS rv;

	-- Add the tasks
	WITH tasks AS (
		INSERT INTO nimrod_tasks(exp_id, name)
			SELECT eexp_id, jsonb_object_keys(_exp->'tasks')::nimrod_task_name
		RETURNING id, name
	)
	SELECT COUNT(_acr_add_task_commands(eexp_id, t.id, _exp->'tasks'->t.name::TEXT)) INTO count_ FROM tasks AS t;

	-- Add the jobs
	PERFORM add_multiple_jobs(eexp_id, _exp->'jobs');

	SELECT * INTO exp_row FROM nimrod_full_experiments WHERE id = eexp_id;
	RETURN exp_row;
END $$ LANGUAGE 'plpgsql' VOLATILE;
