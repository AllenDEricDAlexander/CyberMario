CREATE TABLE room_space (
    id BIGSERIAL PRIMARY KEY,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT NOT NULL,
    room_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT,
    visibility VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 0,
    current_member_count INTEGER NOT NULL DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_room_space_code UNIQUE (room_code),
    CONSTRAINT uk_room_space_context UNIQUE (context_type, context_id)
);

CREATE TABLE room_member (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_status BOOLEAN DEFAULT TRUE,
    seat_no INTEGER,
    display_name VARCHAR(128) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_room_member_room_user_active UNIQUE (room_id, user_id, active_status),
    CONSTRAINT uk_room_member_room_seat_active UNIQUE (room_id, seat_no, active_status)
);

CREATE TABLE room_invitation (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT,
    invitation_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_status BOOLEAN DEFAULT TRUE,
    target_seat_no INTEGER,
    expires_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_room_invitation_code UNIQUE (invitation_code),
    CONSTRAINT uk_room_invitation_room_target_seat_active UNIQUE (room_id, target_seat_no, active_status)
);

CREATE TABLE room_ban (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    banned_by_user_id BIGINT NOT NULL,
    reason VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_room_ban_room_user UNIQUE (room_id, user_id)
);

CREATE TABLE im_channel (
    id BIGSERIAL PRIMARY KEY,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT NOT NULL,
    channel_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_channel_context_key UNIQUE (context_type, context_id, channel_key)
);

CREATE TABLE im_group (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    group_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_group_channel_key UNIQUE (channel_id, group_key)
);

CREATE TABLE im_conversation (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_id BIGINT NOT NULL,
    participant_key VARCHAR(256) NOT NULL,
    conversation_type VARCHAR(32) NOT NULL,
    title VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    message_seq BIGINT NOT NULL DEFAULT 0,
    last_message_id BIGINT,
    last_message_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_conversation_group_scope_type_participant UNIQUE (
        group_id, scope_type, scope_id, conversation_type, participant_key
    )
);

CREATE TABLE im_conversation_member (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    participant_key VARCHAR(256) NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_read_message_seq BIGINT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_conversation_member_user UNIQUE (conversation_id, user_id)
);

CREATE TABLE im_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_member_id BIGINT,
    sender_user_id BIGINT,
    message_seq BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    payload_json JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_message_conversation_seq UNIQUE (conversation_id, message_seq)
);

CREATE TABLE im_read_state (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    conversation_member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_seq BIGINT NOT NULL DEFAULT 0,
    last_read_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_read_state_conversation_user UNIQUE (conversation_id, user_id)
);

CREATE TABLE clocktower_room_profile (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    storyteller_user_id BIGINT,
    player_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    current_game_id BIGINT,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_room_profile_room UNIQUE (room_id)
);

CREATE TABLE clocktower_room_seat (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    room_member_id BIGINT,
    seat_no INTEGER NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    is_traveler BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_room_seat_room_no UNIQUE (room_id, seat_no),
    CONSTRAINT uk_clocktower_room_seat_room_user UNIQUE (room_id, user_id)
);

CREATE TABLE clocktower_game (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL,
    game_no INTEGER NOT NULL,
    script_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    board_snapshot_json JSONB NOT NULL DEFAULT '{}',
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_game_room_no UNIQUE (room_id, game_no)
);

CREATE TABLE clocktower_game_seat (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    room_seat_id BIGINT,
    seat_no INTEGER NOT NULL,
    user_id BIGINT,
    display_name VARCHAR(128) NOT NULL,
    role_code VARCHAR(64),
    role_type VARCHAR(32),
    alignment VARCHAR(32),
    life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE',
    public_life_status VARCHAR(32) NOT NULL DEFAULT 'ALIVE',
    has_dead_vote BOOLEAN NOT NULL DEFAULT TRUE,
    is_traveler BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_game_seat_game_no UNIQUE (game_id, seat_no)
);

CREATE TABLE clocktower_game_event (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    event_seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    day_no INTEGER NOT NULL DEFAULT 0,
    night_no INTEGER NOT NULL DEFAULT 0,
    actor_game_seat_id BIGINT,
    target_game_seat_id BIGINT,
    visibility VARCHAR(32) NOT NULL,
    visible_game_seat_ids_json JSONB NOT NULL DEFAULT '[]',
    payload_json JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_clocktower_game_event_game_seq UNIQUE (game_id, event_seq)
);

CREATE INDEX idx_room_space_context_status ON room_space (context_type, context_id, status);
CREATE INDEX idx_room_space_owner_status ON room_space (owner_user_id, status);
CREATE INDEX idx_room_space_last_active ON room_space (last_active_at);
CREATE INDEX idx_room_member_room_status ON room_member (room_id, status);
CREATE INDEX idx_room_member_user_status ON room_member (user_id, status);
CREATE INDEX idx_room_member_active_status ON room_member (room_id, active_status);
CREATE INDEX idx_room_invitation_room_status ON room_invitation (room_id, status);
CREATE INDEX idx_room_invitation_invitee_status ON room_invitation (invitee_user_id, status);
CREATE INDEX idx_room_ban_room_status ON room_ban (room_id, status);
CREATE INDEX idx_room_ban_user_status ON room_ban (user_id, status);

CREATE INDEX idx_im_channel_context_status ON im_channel (context_type, context_id, status);
CREATE INDEX idx_im_group_channel_status ON im_group (channel_id, status);
CREATE INDEX idx_im_conversation_channel_status ON im_conversation (channel_id, status);
CREATE INDEX idx_im_conversation_group_status ON im_conversation (group_id, status);
CREATE INDEX idx_im_conversation_context_status ON im_conversation (context_type, context_id, status);
CREATE INDEX idx_im_conversation_scope_status ON im_conversation (group_id, scope_type, scope_id, conversation_type, status);
CREATE INDEX idx_im_conversation_last_active ON im_conversation (last_active_at);
CREATE INDEX idx_im_conversation_member_user_status ON im_conversation_member (user_id, status);
CREATE INDEX idx_im_conversation_member_conversation_status ON im_conversation_member (conversation_id, status);
CREATE INDEX idx_im_message_sender ON im_message (sender_user_id);
CREATE INDEX idx_im_read_state_conversation_member ON im_read_state (conversation_id, conversation_member_id);
CREATE INDEX idx_im_read_state_user ON im_read_state (user_id);

CREATE INDEX idx_clocktower_room_profile_current_game ON clocktower_room_profile (current_game_id);
CREATE INDEX idx_clocktower_room_profile_status ON clocktower_room_profile (status);
CREATE INDEX idx_clocktower_room_seat_room_status ON clocktower_room_seat (room_id, status);
CREATE INDEX idx_clocktower_room_seat_user_status ON clocktower_room_seat (user_id, status);
CREATE INDEX idx_clocktower_game_room_status ON clocktower_game (room_id, status);
CREATE INDEX idx_clocktower_game_last_active ON clocktower_game (last_active_at);
CREATE INDEX idx_clocktower_game_seat_game_status ON clocktower_game_seat (game_id, status);
CREATE INDEX idx_clocktower_game_seat_user ON clocktower_game_seat (user_id);
CREATE INDEX idx_clocktower_game_event_game_phase ON clocktower_game_event (game_id, phase, day_no, night_no);
CREATE INDEX idx_clocktower_game_event_visibility ON clocktower_game_event (game_id, visibility);
