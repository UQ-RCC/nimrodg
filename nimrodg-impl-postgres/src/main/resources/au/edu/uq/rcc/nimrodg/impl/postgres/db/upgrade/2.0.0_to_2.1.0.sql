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

    IF _currver != ARRAY[2, 0, 0] THEN
        RAISE EXCEPTION 'Cannot upgrade, require version 2.0.0, got %.%.%', _currver[1], _currver[2], _currver[3];
    END IF;

    RAISE NOTICE 'Upgrading from 2.0.0 to 2.1.0...';

    CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 2, 1, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    --
    -- Add a secret_key field to the agents table.
    -- - Make sure there's no active agents as this will break them.
    -- - Prepend 'upgrade_' to any existing agents' keys so the user can easily see.
    --
    SELECT COUNT(*) INTO _count FROM nimrod_resource_agents WHERE expired = FALSE;
    IF _count != 0 THEN
        RAISE EXCEPTION '% active agent(s), please terminate them before upgrading.', _count;
    END IF;

    --
    -- Create a random hex token of a specified length.
    --
    CREATE OR REPLACE FUNCTION _generate_random_token(_length INT) RETURNS TEXT AS $$
        SELECT string_agg(to_hex(width_bucket(random(), 0, 1, 16)-1), '') FROM generate_series(1, _length);
    $$ LANGUAGE SQL VOLATILE;

    ALTER TABLE nimrod_resource_agents ADD COLUMN secret_key TEXT NOT NULL DEFAULT _generate_random_token(32);
    ALTER TABLE nimrod_resource_agents DISABLE TRIGGER t_res_agent_expire_on_shutdown;
    UPDATE      nimrod_resource_agents SET secret_key = 'upgrade_' || secret_key;
    ALTER TABLE nimrod_resource_agents ENABLE TRIGGER t_res_agent_expire_on_shutdown;

    CREATE OR REPLACE FUNCTION add_agent(
        _state              nimrod_agent_state,
        _queue              TEXT,
        _uuid               UUID,
        _shutdown_signal    INTEGER,
        _shutdown_reason    nimrod_agent_shutdown_reason,
        _expiry_time        TIMESTAMP WITH TIME ZONE,
        _secret_key         TEXT,
        _location           BIGINT,
        _actuator_data      JSONB
    ) RETURNS nimrod_resource_agents AS $$
        INSERT INTO nimrod_resource_agents(
            state,
            queue,
            agent_uuid,
            shutdown_signal,
            shutdown_reason,
            expiry_time,
            secret_key,
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
            COALESCE(_secret_key, _generate_random_token(32)),
            _location,
            _actuator_data
        )
        RETURNING *;
    $$ LANGUAGE SQL;

    --
    -- Legacy version of add_agent(), generates a random secret key.
    -- DEPRECATED, will remove next major version bump.
    --
    CREATE OR REPLACE FUNCTION add_agent(
        _state              nimrod_agent_state,
        _queue              TEXT,
        _uuid               UUID,
        _shutdown_signal    INTEGER,
        _shutdown_reason    nimrod_agent_shutdown_reason,
        _expiry_time        TIMESTAMP WITH TIME ZONE,
        _location           BIGINT,
        _actuator_data      JSONB
    ) RETURNS nimrod_resource_agents AS $$
        SELECT * FROM add_agent(
            _state,
            _queue,
            _uuid,
            _shutdown_signal,
            _shutdown_reason,
            _expiry_time,
            NULL,
            _location,
            _actuator_data
        );
    $$ LANGUAGE SQL;

    RAISE NOTICE 'Done.';
END $upgrade$;
