ALTER TABLE clocktower_board_config
    ADD COLUMN valid BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_clocktower_board_config_owner_valid
    ON clocktower_board_config (created_by, valid, id);
