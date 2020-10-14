--
-- This was done pre-versioning.
-- Do NOT use unless you know what you're doing.
--
DO $upgrade$
BEGIN
    RAISE EXCEPTION 'Not being stupid, remove this line if you want to use this';

    DROP FUNCTION update_agent(UUID, nimrod_agent_state, TEXT, INTEGER, nimrod_agent_shutdown_reason, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, BOOLEAN);

    CREATE OR REPLACE FUNCTION update_agent(_uuid UUID, _state nimrod_agent_state, _queue TEXT, _signal INTEGER, _reason nimrod_agent_shutdown_reason, _connected_at TIMESTAMP WITH TIME ZONE, _last_heard_from TIMESTAMP WITH TIME ZONE, _expiry_time TIMESTAMP WITH TIME ZONE, _expired BOOLEAN, _actuator_data JSONB) RETURNS SETOF nimrod_resource_agents AS $$
        UPDATE nimrod_resource_agents
        SET
                state = _state,
                queue = _queue,
                shutdown_signal = _signal,
                shutdown_reason = _reason,
                connected_at = _connected_at,
                last_heard_from = _last_heard_from,
                expiry_time = _expiry_time,
                expired = _expired,
                actuator_data = _actuator_data
        WHERE
                agent_uuid = _uuid
        RETURNING *;
    $$ LANGUAGE SQL;

    RAISE NOTICE 'Done.';
END $upgrade$;
