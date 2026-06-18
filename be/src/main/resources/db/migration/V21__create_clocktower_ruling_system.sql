ALTER TABLE clocktower_seat
    ADD COLUMN public_life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE';

UPDATE clocktower_seat
SET public_life_status = life_status
WHERE public_life_status = 'ALIVE';

CREATE TABLE clocktower_ruling (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    ruling_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    target_seat_id BIGINT,
    nomination_id BIGINT,
    target_phase VARCHAR(32),
    public_life_status VARCHAR(32),
    winner VARCHAR(32),
    reason VARCHAR(64) NOT NULL,
    note TEXT NOT NULL,
    public_note TEXT,
    visibility VARCHAR(32) NOT NULL,
    undo_of_ruling_id BIGINT,
    event_ids_json TEXT NOT NULL DEFAULT '[]',
    snapshot_json TEXT NOT NULL DEFAULT '{}',
    revoked_by BIGINT,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_clocktower_ruling_room ON clocktower_ruling (room_id);
CREATE INDEX idx_clocktower_ruling_target_seat ON clocktower_ruling (target_seat_id);
CREATE INDEX idx_clocktower_ruling_nomination ON clocktower_ruling (nomination_id);
