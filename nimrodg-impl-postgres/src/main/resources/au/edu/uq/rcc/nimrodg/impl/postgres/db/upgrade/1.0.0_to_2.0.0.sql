DO $upgrade$
DECLARE
    _oid INTEGER;
    _count INTEGER;
    _currver INTEGER[];
BEGIN
    SELECT oid INTO _oid FROM pg_proc WHERE proname = 'get_schema_version';

    IF _oid IS NULL THEN
        RAISE EXCEPTION 'No schema version, is this a Nimrod database?';
    END IF;

    SELECT ARRAY[major, minor, patch] INTO _currver FROM get_schema_version();

    RAISE NOTICE '%', _currver;

    IF _currver != ARRAY[1, 0, 0] THEN
        RAISE EXCEPTION 'Cannot upgrade, require version 1.0.0, got %.%.%', _currver[1], _currver[2], _currver[3];
    END IF;

    RAISE NOTICE 'Upgrading from 1.0.0 to 2.0.0...';

    CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 2, 0, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    DROP FUNCTION get_experiment(_exp_id BIGINT);
    DROP FUNCTION get_experiment(_name TEXT);
    DROP FUNCTION get_experiments();
    DROP FUNCTION add_compiled_experiment(_name TEXT, _work_dir TEXT, _file_token TEXT, _exp JSONB);
    DROP VIEW nimrod_full_experiments;
    ALTER TABLE nimrod_experiments DROP COLUMN file_token;
    CREATE VIEW nimrod_full_experiments AS(
        SELECT
            e.*,
            array_to_json(e.variables) AS vars_json,
            export_tasks(e.id) AS tasks_json
        FROM
            nimrod_experiments AS e
    );

    CREATE OR REPLACE FUNCTION get_experiment(_exp_id BIGINT) RETURNS SETOF nimrod_full_experiments AS $$
        SELECT * FROM nimrod_full_experiments WHERE id = _exp_id;
    $$ LANGUAGE SQL STABLE;

    CREATE OR REPLACE FUNCTION get_experiment(_name TEXT) RETURNS SETOF nimrod_full_experiments AS $$
        SELECT * FROM nimrod_full_experiments WHERE name = _name;
    $$ LANGUAGE SQL STABLE;

    CREATE OR REPLACE FUNCTION get_experiments() RETURNS SETOF nimrod_full_experiments AS $$
        SELECT * FROM nimrod_full_experiments;
    $$ LANGUAGE SQL STABLE;

    CREATE OR REPLACE FUNCTION add_compiled_experiment(_name TEXT, _work_dir TEXT, _exp JSONB) RETURNS nimrod_full_experiments AS $$
    DECLARE
        count_ BIGINT;

        vars TEXT[];
        eexp_id BIGINT;
        exp_row nimrod_full_experiments;
    BEGIN
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

        -- Ensure work_dir is a directory.
        IF _work_dir NOT LIKE '%/' THEN
            _work_dir := _work_dir || '/';
        END IF;

        -- Add the experiment
        INSERT INTO nimrod_full_experiments(name, work_dir, variables, path)
        VALUES(_name, _work_dir, vars, _name)
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


    ALTER TABLE nimrod_job_attempts DROP COLUMN token;
    DROP FUNCTION _generate_random_token(INT);
    DROP FUNCTION is_token_valid_for_experiment_storage(BIGINT, TEXT);

    RAISE NOTICE 'Done.';
END $upgrade$;
