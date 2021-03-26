.bail on

BEGIN TRANSACTION;

--
-- Check our schema is the correct version.
--
UPDATE nimrod_schema_version SET major = 2, minor = 1, patch = 0;

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

--
-- All changes done, now actually update the version.
--
DELETE FROM nimrod_schema_version;
INSERT INTO nimrod_schema_version(major, minor, patch) VALUES(3, 0, 0);

COMMIT;
