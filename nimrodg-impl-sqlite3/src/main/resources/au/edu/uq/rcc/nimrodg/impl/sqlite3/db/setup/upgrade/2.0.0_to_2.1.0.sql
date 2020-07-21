.bail on

BEGIN TRANSACTION;

--
-- Check our schema is the correct version.
--
UPDATE nimrod_schema_version SET major = 1, minor = 0, patch = 0;

--
-- SQLite's ADD COLUMN support is minimal.
-- Replace the old table with a new one.
--

CREATE TABLE nimrod_resource_agents2(
    id              INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    state           TEXT NOT NULL CHECK(state IN ('WAITING_FOR_HELLO', 'READY', 'BUSY', 'SHUTDOWN')),
    queue           TEXT CHECK((state IN ('WAITING_FOR_HELLO', 'SHUTDOWN') AND queue IS NULL) OR (queue IS NOT NULL)),
    agent_uuid      UUID NOT NULL UNIQUE,
    shutdown_signal INTEGER NOT NULL,
    shutdown_reason TEXT NOT NULL CHECK(shutdown_reason IN ('HostSignal', 'Requested')),
    /* From here, none of this is actually state. */
    created         INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
    connected_at    INTEGER DEFAULT NULL CHECK(connected_at >= created),
    last_heard_from INTEGER DEFAULT NULL CHECK(last_heard_from >= created),
    expiry_time     INTEGER DEFAULT NULL CHECK(expiry_time >= created),
    expired_at      INTEGER DEFAULT NULL CHECK(expired_at >= created),
    expired         BOOLEAN NOT NULL DEFAULT FALSE,
    secret_key      TEXT NOT NULL DEFAULT (LOWER(HEX(RANDOMBLOB(16)))),
    location        INTEGER REFERENCES nimrod_resources(id) ON DELETE CASCADE,
    actuator_data   TEXT
);

INSERT INTO nimrod_resource_agents2(
    id, state, queue, agent_uuid, shutdown_signal, shutdown_reason,
    created, connected_at, last_heard_from, expiry_time, expired_at, expired,
    location, actuator_data
) SELECT * FROM nimrod_resource_agents;

DROP TABLE nimrod_resource_agents;
ALTER TABLE nimrod_resource_agents2 RENAME TO nimrod_resource_agents;
CREATE INDEX i_agent_location ON nimrod_resource_agents(location) WHERE location IS NOT NULL;

CREATE TRIGGER t_agent_expire_on_shutdown AFTER UPDATE ON nimrod_resource_agents FOR EACH ROW WHEN OLD.state != 'SHUTDOWN' AND NEW.state = 'SHUTDOWN'
BEGIN
    UPDATE nimrod_resource_agents SET expired_at = strftime('%s', 'now'), expired = TRUE WHERE id = OLD.id;
END;

CREATE TRIGGER t_agent_connect AFTER UPDATE ON nimrod_resource_agents FOR EACH ROW WHEN OLD.state = 'WAITING_FOR_HELLO' AND NEW.state = 'READY'
BEGIN
    UPDATE nimrod_resource_agents SET connected_at = strftime('%s', 'now') WHERE id = OLD.id;
END;

--
-- All changes done, now actually update the version.
--
DELETE FROM nimrod_schema_version;
INSERT INTO nimrod_schema_version(major, minor, patch) VALUES(1, 1, 0);

COMMIT;
