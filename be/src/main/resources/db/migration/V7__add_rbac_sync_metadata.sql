ALTER TABLE sys_permission
    ADD COLUMN managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_permission
    ADD COLUMN owner_app VARCHAR(64);

ALTER TABLE sys_permission
    ADD COLUMN source_type VARCHAR(32);

ALTER TABLE sys_permission
    ADD COLUMN source_key VARCHAR(256);

ALTER TABLE sys_permission
    ADD COLUMN sync_hash VARCHAR(64);

ALTER TABLE sys_permission
    ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE sys_permission
    ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_permission_owner_app ON sys_permission (owner_app);
CREATE INDEX idx_permission_managed ON sys_permission (managed);
CREATE INDEX idx_permission_source_key ON sys_permission (source_key);

ALTER TABLE sys_role
    ADD COLUMN managed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sys_role
    ADD COLUMN owner_app VARCHAR(64);

ALTER TABLE sys_role
    ADD COLUMN source_type VARCHAR(32);

ALTER TABLE sys_role
    ADD COLUMN source_key VARCHAR(256);

ALTER TABLE sys_role
    ADD COLUMN sync_hash VARCHAR(64);

ALTER TABLE sys_role
    ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_role_owner_app ON sys_role (owner_app);
CREATE INDEX idx_role_managed ON sys_role (managed);
CREATE INDEX idx_role_source_key ON sys_role (source_key);
