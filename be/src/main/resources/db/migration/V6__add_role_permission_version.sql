ALTER TABLE sys_role
    ADD COLUMN permission_version BIGINT NOT NULL DEFAULT 0;
