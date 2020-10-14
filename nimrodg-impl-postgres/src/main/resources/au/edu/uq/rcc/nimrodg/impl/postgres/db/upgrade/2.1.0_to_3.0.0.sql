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

    IF _currver != ARRAY[2, 1, 0] THEN
        RAISE EXCEPTION 'Cannot upgrade, require version 2.1.0, got %.%.%', _currver[1], _currver[2], _currver[3];
    END IF;

    RAISE NOTICE 'Upgrading from 2.1.0 to 3.0.0...';

    CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 3, 0, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    DROP FUNCTION delete_experiment(_exp_id BIGINT);
    DROP FUNCTION delete_experiment(_name TEXT);
    DROP FUNCTION update_experiment_state(_exp_id BIGINT, _state nimrod_experiment_state);
    DROP FUNCTION get_job_attempt(_att_id BIGINT);
    DROP FUNCTION get_jobs_by_id(_ids BIGINT[]);
    DROP FUNCTION get_experiment(_exp_id BIGINT);
    DROP FUNCTION get_experiment(_name TEXT);
    DROP FUNCTION get_experiments();
    DROP FUNCTION get_resource_type_info(_type TEXT);
    DROP FUNCTION get_resource_type_info();
    DROP FUNCTION get_resource(_name nimrod_identifier);
    DROP FUNCTION delete_resource(_id BIGINT);
    DROP FUNCTION get_resources();
    DROP FUNCTION unassign_resource(_res_id BIGINT, _exp_id BIGINT);
    DROP FUNCTION get_assignment_status(_res_id BIGINT, _exp_id BIGINT);
    DROP FUNCTION is_resource_capable(_res_id BIGINT, _exp_id BIGINT);
    DROP FUNCTION add_resource_caps(_res_id BIGINT, _exp_id BIGINT);
    DROP FUNCTION remove_resource_caps(_res_id BIGINT, _exp_id BIGINT);
    DROP FUNCTION get_agent_information(_uuid UUID);
    DROP FUNCTION get_agents_on_resource(_res_id BIGINT);
    DROP FUNCTION add_agent(_state nimrod_agent_state, _queue TEXT, _uuid UUID, _shutdown_signal INTEGER,
        _shutdown_reason    nimrod_agent_shutdown_reason, _expiry_time TIMESTAMP WITH TIME ZONE, _location BIGINT,
        _actuator_data JSONB);
    DROP FUNCTION update_agent(_uuid UUID, _state nimrod_agent_state, _queue TEXT,
        _signal INTEGER, _reason nimrod_agent_shutdown_reason, _connected_at TIMESTAMP WITH TIME ZONE,
        _last_heard_from TIMESTAMP WITH TIME ZONE, _expiry_time TIMESTAMP WITH TIME ZONE, _expired BOOLEAN);

    RAISE NOTICE 'Done.';
END $upgrade$;
