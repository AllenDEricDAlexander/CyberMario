CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_room_space_clocktower_audit_created
    ON room_space (created_at DESC, id DESC)
    WHERE context_type = 'CLOCKTOWER_ROOM' AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_space_clocktower_audit_name_trgm
    ON room_space USING GIN (LOWER(name) gin_trgm_ops)
    WHERE context_type = 'CLOCKTOWER_ROOM' AND deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_clocktower_game_audit_room_started
    ON clocktower_game (room_id, (COALESCE(started_at, created_at)) DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_clocktower_game_audit_started
    ON clocktower_game ((COALESCE(started_at, created_at)) DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_clocktower_game_event_audit_game_occurred
    ON clocktower_game_event (game_id, occurred_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_clocktower_game_event_audit_occurred
    ON clocktower_game_event (occurred_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_member_audit_room_joined
    ON room_member (room_id, joined_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_member_audit_joined
    ON room_member (joined_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_invitation_audit_room_created
    ON room_invitation (room_id, created_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_invitation_audit_created
    ON room_invitation (created_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_ban_audit_room_created
    ON room_ban (room_id, created_at DESC, id DESC)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_room_ban_audit_created
    ON room_ban (created_at DESC, id DESC)
    WHERE deleted = FALSE;
