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
/*
** Given '["x", "y", "z"]', return
**  idx | value
** -----+------
**    0 | "x"
**    1 | "y"
**    2 | "z"
*/
CREATE OR REPLACE FUNCTION _acr_array_value_unpack_with_index(_array JSONB) RETURNS TABLE(idx BIGINT, value JSONB) AS $$
	SELECT (_idx - 1), _value FROM jsonb_array_elements(_array) WITH ORDINALITY AS t(_value, _idx);
$$ LANGUAGE SQL IMMUTABLE;



/*
** Given a JSONB array of arrays '[[0,0],[1,0]]' (job 1 has indices [0, 0], job 2 has indices [1, 0]),
** add the jobs to the experiment and return the job ids and indices as a JSONB array.
**  job_id | job_index | value_indices
** --------+-----------+---------------
**     197 |         1 | [0, 0]
**     198 |         2 | [1, 0]
*/
CREATE OR REPLACE FUNCTION _acr_add_jobs_and_map_indices(_exp_id BIGINT, _jobs JSONB) RETURNS TABLE(job_id BIGINT, job_index BIGINT, value_indices JSONB) AS $$
	WITH job_info AS (
		SELECT row_number() OVER() AS _job_index, _indices
			FROM jsonb_array_elements(_jobs) AS _indices
		/*
		**	 job_index | indices
		**	-----------+---------
		**			 1 | [0, 0]
		**			 2 | [1, 0]
		*/
	), jobs AS (
		INSERT INTO nimrod_jobs(exp_id, job_index)
		SELECT
			_exp_id,
			ji._job_index
		FROM job_info AS ji
		RETURNING nimrod_jobs.id AS _id, nimrod_jobs.job_index AS _job_index
		/*
		**	 job_id | job_index
		**	--------+-----------
		**		123 | 1
		**		124 | 2
		*/
	)
	SELECT j._id, j._job_index, ji._indices
	FROM
		jobs AS j,
		job_info AS ji
	WHERE
		j._job_index = ji._job_index;
$$ LANGUAGE SQL VOLATILE;

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

CREATE OR REPLACE FUNCTION add_compiled_experiment_for_batch(_name TEXT, _work_dir TEXT, _file_token TEXT, _vars TEXT[], _tasks JSONB) RETURNS nimrod_full_experiments AS $$
DECLARE
	dummy BIGINT;
    num_reserved BIGINT;
	eexp_id BIGINT;
	exp_row nimrod_full_experiments;
BEGIN
	/* Check the experiment has no variables with reserved names. */
    WITH names AS(
		SELECT unnest(_vars) AS vars
		INTERSECT ALL
		SELECT name FROM nimrod_reserved_variables
	)
	SELECT COUNT(*) INTO num_reserved FROM names;

    IF num_reserved != 0 THEN
		RAISE EXCEPTION 'Experiment cannot have variables with reserved names.';
	END IF;

	SELECT array_cat(_vars, ARRAY['jobindex', 'jobname']::TEXT[]) INTO _vars;

	IF _file_token IS NULL THEN
		_file_token := _generate_random_token(32);
	END IF;

	/* Add the experiments */
	INSERT INTO nimrod_experiments(name, work_dir, file_token, path)
	VALUES(_name, _work_dir, _file_token, _name)
	RETURNING id INTO eexp_id;

	/* Add the variables */
	INSERT INTO nimrod_variables(exp_id, name)
	SELECT eexp_id, unnest(_vars);

	/* Add the tasks */
	WITH tasks AS (
		INSERT INTO nimrod_tasks(exp_id, name)
			SELECT eexp_id, jsonb_object_keys(_tasks)::nimrod_task_name
		RETURNING id, name
	)
	SELECT COUNT(_acr_add_task_commands(eexp_id, t.id, _tasks->t.name::TEXT)) INTO dummy FROM tasks AS t;

	SELECT * INTO exp_row FROM nimrod_full_experiments WHERE id = eexp_id;
	RETURN exp_row;
END
$$ LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION add_compiled_experiment(_name TEXT, _work_dir TEXT, _file_token TEXT, _exp JSONB) RETURNS nimrod_full_experiments AS $$
DECLARE
	experiment_row nimrod_full_experiments;
	vars TEXT[];
BEGIN
	/* Convert the variables list to an array */
	SELECT array_agg(value->>'name') INTO vars FROM jsonb_array_elements(_exp->'variables');

	/* Add the experiment */
	SELECT
		* INTO experiment_row
	FROM
		add_compiled_experiment_for_batch(_name, _work_dir, _file_token, vars, _exp->'tasks')
	;

	/*
	**  var_id | var_index | var_name
	** --------+-----------+----------
	**     170 |         0 | x
	**     171 |         1 | y
	*/
	CREATE TEMPORARY TABLE varinfo(
		var_id BIGINT NOT NULL PRIMARY KEY,
		var_index BIGINT NOT NULL UNIQUE,
		var_name nimrod_variable_identifier NOT NULL UNIQUE,
		UNIQUE(var_index, var_name)
	) ON COMMIT DROP;

	WITH vvars AS(
		SELECT * FROM unnest(vars) WITH ORDINALITY AS t(var_name, var_index)
	)
	INSERT INTO varinfo(var_id, var_index, var_name)
	SELECT
		v.id AS var_id,
		vv.var_index - 1,
		vv.var_name
	FROM
		vvars AS vv LEFT JOIN
		nimrod_variables AS v ON v.name = vv.var_name
	WHERE
		v.exp_id = experiment_row.id
	;

	/* Add our jobs and their variables. */
	WITH jobs AS (
		SELECT job_id, job_index, value_indices FROM _acr_add_jobs_and_map_indices(experiment_row.id, _exp->'jobs')
	)
	INSERT INTO nimrod_job_variables(job_id, variable_id, value)
	SELECT j.job_id, v.var_id, _exp->'variables'->v.var_index::INT->'values'->>k.value::TEXT::INT
	FROM
		jobs AS j,
		varinfo AS v,
		_acr_array_value_unpack_with_index(j.value_indices) AS k
	WHERE v.var_index = k.idx;

	-- TODO: Add a function, update_implicit_variables_for_job() that handles ALL of the implicit variables.
	/* Add $jobindex and $jobname. */
	INSERT INTO nimrod_job_variables(job_id, variable_id, value)
	SELECT
		j.id,
		v.id,
		j.job_index::TEXT
	FROM
		nimrod_jobs AS j CROSS JOIN
		nimrod_variables AS v
	WHERE
		j.exp_id = experiment_row.id AND
		v.exp_id = experiment_row.id AND
		v.name IN ('jobindex', 'jobname')
	;
	RETURN experiment_row;
END
$$ LANGUAGE 'plpgsql' VOLATILE;
