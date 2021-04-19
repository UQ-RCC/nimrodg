--
-- Nimrod/G
-- https://github.com/UQ-RCC/nimrodg
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
CREATE OR REPLACE FUNCTION _amj_unnest_2d_1d(ANYARRAY, OUT a ANYARRAY) RETURNS SETOF ANYARRAY AS $$
BEGIN
    FOREACH a SLICE 1 IN ARRAY $1 LOOP
        RETURN NEXT;
    END LOOP;
END
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION _amj_json_to_2darray(_values JSONB) RETURNS TEXT[][] AS $$
    SELECT
        array_agg(v2.arr)
    FROM
        jsonb_array_elements(_values) AS v1
    LEFT JOIN LATERAL (
        SELECT
            array_agg(vv)::TEXT[] AS arr
        FROM
            jsonb_array_elements_text(v1) AS vv
    ) AS v2 ON TRUE
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION add_multiple_jobs_internal(_exp_id BIGINT, _vars TEXT[], _values TEXT[][]) RETURNS SETOF BIGINT AS $$
DECLARE
    var_count BIGINT;
    value_count BIGINT;
    job_count BIGINT;
    num_reserved BIGINT;
    next_jobindex BIGINT;
BEGIN
    SELECT array_length(_vars, 1) INTO var_count;
    SELECT array_length(_values, 2) INTO value_count;
    SELECT array_length(_values, 1) INTO job_count;

    IF var_count != value_count THEN
        RAISE EXCEPTION 'Mismatched variable/value count (% != %)', var_count, value_count;
    END IF;

    -- Check for reserved names
    WITH names AS(
        SELECT unnest(_vars) AS vars
        INTERSECT ALL
        SELECT name FROM nimrod_reserved_variables
    )
    SELECT COUNT(*) INTO num_reserved FROM names;

    IF num_reserved != 0 THEN
        RAISE EXCEPTION 'Job cannot have variables with reserved names';
    END IF;

    -- Get the next available job index
    SELECT
        COALESCE(MAX(j.job_index) + 1, 1) INTO next_jobindex
    FROM
        nimrod_jobs AS j
    WHERE
        j.exp_id = _exp_id
    ;

    CREATE TEMPORARY TABLE IF NOT EXISTS variables(
        var_id BIGINT UNIQUE,
        var_index BIGINT NOT NULL UNIQUE,
        var_name TEXT NOT NULL UNIQUE,
        UNIQUE(var_index, var_name)
    ) ON COMMIT DROP;
    DELETE FROM variables;

    --
    -- Map our variables to the experiments's variables.
    -- The sizes will be different if they don't match.
    --
    INSERT INTO variables(var_id, var_index, var_name)
    SELECT
        v.id,
        vv.var_index,
        vv.var_name
    FROM
        nimrod_variables AS v
    FULL OUTER JOIN
        unnest(_vars) WITH ORDINALITY AS vv(var_name, var_index)
        ON vv.var_name = v.name
    WHERE
        v.exp_id = _exp_id OR
        v.id IS NULL
    ;

-- 	RAISE NOTICE '
-- %', (SELECT string_agg((v.*)::RECORD::TEXT, '
-- '::TEXT ORDER BY var_index) FROM variables AS v);

    IF (SELECT COUNT(*) FROM variables WHERE var_id IS NULL) != 0 THEN
        RAISE EXCEPTION 'Variable mismatch';
    END IF;

    CREATE TEMPORARY TABLE IF NOT EXISTS jobs(
        job_id BIGINT,
        job_index BIGINT NOT NULL,
        values TEXT[] NOT NULL
    ) ON COMMIT DROP;
    DELETE FROM jobs;

    --RAISE NOTICE 'next jobindex %', next_jobindex;
    --RAISE NOTICE 'value_count = %', value_count;

    INSERT INTO jobs(job_id, job_index, values)
    SELECT
        NULL,
        ji,
        j.values
    FROM
        _amj_unnest_2d_1d(_values) WITH ORDINALITY AS j(values, job_index)
    LEFT JOIN
        generate_series(next_jobindex, next_jobindex + job_count) AS ji
        ON j.job_index + next_jobindex - 1 = ji
    ;

    -- Update var count
    SELECT array_length(_vars, 1) INTO var_count;

    -- Add the jobs and get their ids.
    WITH j AS(
        INSERT INTO nimrod_jobs(exp_id, job_index)
        SELECT
            _exp_id,
            job_index
        FROM
            jobs
        RETURNING id, job_index
    )
    UPDATE jobs
    SET job_id = j.id
    FROM j
    WHERE jobs.job_index = j.job_index;

    RETURN QUERY
    SELECT
        job_id
    FROM
        jobs AS j
    ;
END
$$ LANGUAGE 'plpgsql' VOLATILE;
