.bail on

BEGIN TRANSACTION;

CREATE TABLE nimrod_schema_version(
    major INTEGER NOT NULL CHECK(major >= 0),
    minor INTEGER NOT NULL CHECK(minor >= 0),
    patch INTEGER NOT NULL CHECK(patch >= 0),
    CHECK(ROWID == 1)
);

INSERT INTO nimrod_schema_version(major, minor, patch)
VALUES (1, 0, 0);

--
-- SQLite doesn't have stored procedures, so abuse a trigger to compare a schema version.
--
-- UPDATE nimrod_schema_version SET major = 1, minor = 0, patch = 0;
-- - Will RAISE if the schema version isn't 1.0.0
-- - To actually change the version, DELETE and INSERT a new row.
--
CREATE TRIGGER t_update_version BEFORE UPDATE ON nimrod_schema_version FOR EACH ROW
BEGIN
    WITH yy AS(
        SELECT CASE
            WHEN NEW.major > OLD.major THEN 1
            WHEN NEW.major < OLD.major THEN -1
            ELSE CASE
                WHEN NEW.minor > OLD.minor THEN 1
                WHEN NEW.minor < OLD.minor THEN -1
                ELSE CASE
                    WHEN NEW.patch > OLD.patch THEN 1
                    WHEN NEW.patch < OLD.patch THEN -1
                    ELSE 0
                END
            END
        END AS c
    )
    SELECT CASE
        WHEN c < 0 THEN RAISE(ABORT, 'Schema too new, nothing to do')
        WHEN c > 0 THEN RAISE(ABORT, 'Schema too old, refusing upgrade')
        WHEN c = 0 THEN RAISE(IGNORE)
    END FROM yy;
END;

COMMIT;
