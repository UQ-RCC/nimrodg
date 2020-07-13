DO $upgrade$
DECLARE
    _oid INTEGER;
BEGIN
    SELECT oid INTO _oid FROM pg_proc WHERE proname = 'get_schema_version';

    IF _oid IS NOT NULL THEN
        RAISE NOTICE 'Nothing to do.';
        RETURN;
    END IF;

    RAISE NOTICE 'Upgrading from 0.0.0 to 1.0.0...';

    CREATE FUNCTION get_schema_version() RETURNS TABLE(major INTEGER, minor INTEGER, patch INTEGER) AS $$
        SELECT 1, 0, 0;
    $$ LANGUAGE SQL IMMUTABLE;

    CREATE FUNCTION is_schema_compatible(_major INTEGER, _minor INTEGER, _patch INTEGER) RETURNS BOOLEAN AS $$
        SELECT _major = v.major AND _minor <= v.minor AND _patch <= v.patch FROM get_schema_version() AS v;
    $$ LANGUAGE SQL IMMUTABLE;

    CREATE FUNCTION compare_schema_version(_major INTEGER, _minor INTEGER, _patch INTEGER) RETURNS INTEGER AS $$
        SELECT CASE
            WHEN _major > v.major THEN 1
            WHEN _major < v.major THEN -1
            ELSE CASE
                WHEN _minor > v.minor THEN 1
                WHEN _minor < v.minor THEN -1
                ELSE CASE
                    WHEN _patch > v.patch THEN 1
                    WHEN _patch < v.patch THEN -1
                    ELSE 0
                END
            END
        END FROM
            get_schema_version() AS v
        ;
    $$ LANGUAGE SQL IMMUTABLE;

    CREATE FUNCTION require_schema_compatible(major INTEGER, minor INTEGER, patch INTEGER) RETURNS VOID AS $$
        DECLARE
            _major INTEGER;
            _minor INTEGER;
            _patch INTEGER;
        BEGIN
            IF is_schema_compatible(major, minor, patch) THEN
                RETURN;
            END IF;

            SELECT v.major, v.minor, v.patch INTO _major, _minor, _patch FROM get_schema_version() AS v;

            RAISE EXCEPTION 'Incompatible schema (minimum = %.%.%, current = %.%.%)',
                major, minor, patch,
                _major, _minor, _patch
            ;
        END
    $$ LANGUAGE 'plpgsql' IMMUTABLE;

    RAISE NOTICE 'Done.';
END $upgrade$;
