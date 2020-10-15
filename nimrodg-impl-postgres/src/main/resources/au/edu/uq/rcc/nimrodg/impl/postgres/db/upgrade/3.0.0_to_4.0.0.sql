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

    IF _currver != ARRAY[3, 0, 0] THEN
        RAISE EXCEPTION 'Cannot upgrade, require version 3.0.0, got %.%.%', _currver[1], _currver[2], _currver[3];
    END IF;

    RAISE NOTICE 'Upgrading from 3.0.0 to 4.0.0...';

    CREATE OR REPLACE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 4, 0, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    DROP VIEW nimrod_full_resource_assignments;
    CREATE VIEW nimrod_full_resource_assignments AS
    SELECT
        a.id,
        a.exp_id,
        a.resource_id,
        COALESCE(a.tx_uri, r.tx_uri, cfg.tx_uri) AS tx_uri,
        COALESCE(a.tx_cert_path, r.tx_cert_path, cfg.tx_cert_path) AS tx_cert_path,
        COALESCE(a.tx_no_verify_peer, r.tx_no_verify_peer, cfg.tx_no_verify_peer) AS tx_no_verify_peer,
        COALESCE(a.tx_no_verify_host, r.tx_no_verify_host, cfg.tx_no_verify_host) AS tx_no_verify_host
    FROM
        nimrod_resource_assignments AS a INNER JOIN
        nimrod_resources AS r ON a.resource_id = r.id LEFT JOIN
        nimrod_config AS cfg ON cfg.id = 1
    ;
    RAISE NOTICE 'Done.';
END $upgrade$;
