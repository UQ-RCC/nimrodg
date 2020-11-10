DO $upgrade$
DECLARE
    _oid INTEGER;
    _count INTEGER;
    _currver INTEGER[];
BEGIN
    SELECT oid INTO _oid FROM pg_proc WHERE
        proname = 'get_schema_version' AND
        pronamespace = to_regnamespace((SELECT current_schema))::oid
    ;

    IF _oid IS NULL THEN
        RAISE EXCEPTION 'No schema version, is this a Nimrod database?';
    END IF;

    SELECT ARRAY[major, minor, patch] INTO _currver FROM get_schema_version();

    IF _currver != ARRAY[4, 0, 0] THEN
        RAISE EXCEPTION 'Cannot upgrade, require version 4.0.0, got %.%.%', _currver[1], _currver[2], _currver[3];
    END IF;

    RAISE NOTICE 'Upgrading from 4.0.0 to 5.0.0...';

    CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 5, 0, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    --
    -- Job Attempts
    --
    DROP TRIGGER t_exp_attempt_add ON nimrod_job_attempts;
    DROP FUNCTION _exp_t_attempt_add();
    ALTER TABLE nimrod_job_attempts DROP COLUMN path;

    CREATE OR REPLACE FUNCTION _exp_t_attempt_add() RETURNS TRIGGER AS $$
    DECLARE
        _status nimrod_job_status;
    BEGIN
        IF OLD.status = 'NOT_RUN'::nimrod_job_status AND NEW.status = 'RUNNING'::nimrod_job_status THEN
            -- NOT_RUN -> RUNNING
            NEW.start_time = NOW();
        ELSIF OLD.status = 'NOT_RUN'::nimrod_job_status AND NEW.status = 'FAILED'::nimrod_job_status THEN
            -- NOT_RUN -> FAILED
            NEW.start_time = NOW();
            NEW.finish_time = NOW();
        ELSIF OLD.status = 'RUNNING'::nimrod_job_status AND (NEW.status = 'FAILED'::nimrod_job_status OR NEW.status = 'COMPLETED'::nimrod_job_status) THEN
            -- RUNNING -> {FAILED, COMPLETED}
            NEW.finish_time = NOW();
        ELSE
            RAISE EXCEPTION 'Invalid job attempt update (%), cannot transition from % -> %', OLD.id, OLD.status, NEW.status;
        END IF;

        RETURN NEW;
    END $$ LANGUAGE 'plpgsql';
    CREATE TRIGGER t_exp_attempt_add BEFORE UPDATE ON nimrod_job_attempts
        FOR EACH ROW EXECUTE PROCEDURE _exp_t_attempt_add();




    --
    -- Jobs
    --
    DROP FUNCTION filter_jobs(BIGINT, nimrod_job_status[], BIGINT, BIGINT);
    DROP FUNCTION add_multiple_jobs(BIGINT, JSONB);
    DROP VIEW nimrod_full_jobs;

    DROP TRIGGER t_exp_job_add ON nimrod_jobs;
    DROP FUNCTION _exp_t_job_add();
    ALTER TABLE nimrod_jobs DROP COLUMN path;

    CREATE VIEW nimrod_full_jobs AS
        SELECT
            j.*,
            get_job_status(j.id) AS status,
            (j.variables || jsonb_build_object('jobindex', j.job_index::TEXT, 'jobname', j.job_index::TEXT)) AS full_variables
        FROM
            nimrod_jobs AS j
    ;

    CREATE OR REPLACE FUNCTION filter_jobs(_exp_id BIGINT, _status nimrod_job_status[], _start BIGINT, _limit BIGINT) RETURNS SETOF nimrod_full_jobs AS $$
        SELECT
            *
        FROM
            nimrod_full_jobs
        WHERE
            exp_id = _exp_id AND
            job_index >= COALESCE(_start, 0) AND
            status = ANY(COALESCE(_status, enum_range(NULL::nimrod_job_status)))
        ORDER BY job_index ASC
        LIMIT _limit;
    $$ LANGUAGE SQL STABLE;

    CREATE OR REPLACE FUNCTION add_multiple_jobs(_exp_id BIGINT, _jobs JSONB) RETURNS SETOF nimrod_full_jobs AS $$
    DECLARE
        _ids BIGINT[];
    BEGIN
        SELECT array_agg(j) INTO _ids FROM add_multiple_jobs_internal(_exp_id, _jobs) AS j;
        RETURN QUERY SELECT * FROM nimrod_full_jobs WHERE id IN (SELECT unnest(_ids));
    END
    $$ LANGUAGE 'plpgsql' VOLATILE;

    --
    -- Experiments
    --
    DROP FUNCTION add_compiled_experiment(TEXT, TEXT, JSONB);
    DROP VIEW IF EXISTS nimrod_full_experiments;
    ALTER TABLE nimrod_experiments DROP COLUMN path;

    CREATE VIEW nimrod_full_experiments AS(
        SELECT
            e.*,
            array_to_json(e.variables) AS vars_json,
            export_tasks(e.id) AS tasks_json
        FROM
            nimrod_experiments AS e
    );

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
        INSERT INTO nimrod_full_experiments(name, work_dir, variables)
        VALUES(_name, _work_dir, vars)
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

    DROP DOMAIN nimrod_path;

    RAISE NOTICE 'Done.';
END $upgrade$;
