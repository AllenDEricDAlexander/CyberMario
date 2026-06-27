-- V30: create the redesigned IM core schema.

DROP TABLE IF EXISTS im_read_state;
DROP TABLE IF EXISTS im_message;
DROP TABLE IF EXISTS im_conversation_member;
DROP TABLE IF EXISTS im_conversation;
DROP TABLE IF EXISTS im_group;
DROP TABLE IF EXISTS im_channel;

CREATE TABLE im_channel (
    id BIGSERIAL PRIMARY KEY,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT,
    channel_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT,
    visibility VARCHAR(32) NOT NULL,
    join_policy VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    announcement TEXT NOT NULL DEFAULT '',
    main_conversation_id BIGINT,
    member_count INTEGER NOT NULL DEFAULT 0,
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

CREATE INDEX idx_im_channel_last_active_at ON im_channel (last_active_at);

CREATE TABLE im_group (
    id BIGSERIAL PRIMARY KEY,
    channel_id BIGINT,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT,
    group_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT,
    join_policy VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    announcement TEXT NOT NULL DEFAULT '',
    conversation_id BIGINT,
    member_count INTEGER NOT NULL DEFAULT 0,
    last_active_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_group_channel_key UNIQUE (channel_id, group_key)
);

CREATE INDEX idx_im_group_last_active_at ON im_group (last_active_at);

CREATE TABLE im_dm_pair (
    id BIGSERIAL PRIMARY KEY,
    user_lo_id BIGINT NOT NULL,
    user_hi_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_dm_pair_users UNIQUE (user_lo_id, user_hi_id),
    CONSTRAINT chk_im_dm_pair_ordered CHECK (user_lo_id < user_hi_id)
);

CREATE TABLE im_membership (
    id BIGSERIAL PRIMARY KEY,
    surface_type VARCHAR(32) NOT NULL,
    surface_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    muted_until TIMESTAMP WITH TIME ZONE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_membership_surface_user UNIQUE (surface_type, surface_id, user_id)
);

CREATE INDEX idx_im_membership_user_status ON im_membership (user_id, status);
CREATE INDEX idx_im_membership_surface_status ON im_membership (surface_type, surface_id, status);

CREATE TABLE im_join_request (
    id BIGSERIAL PRIMARY KEY,
    surface_type VARCHAR(32) NOT NULL,
    surface_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    decided_by BIGINT,
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_reason VARCHAR(512),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE im_conversation (
    id BIGSERIAL PRIMARY KEY,
    conversation_type VARCHAR(32) NOT NULL,
    owner_surface_type VARCHAR(32) NOT NULL,
    owner_surface_id BIGINT NOT NULL,
    context_type VARCHAR(64) NOT NULL,
    context_id BIGINT,
    message_seq BIGINT NOT NULL DEFAULT 0,
    last_message_id BIGINT,
    last_message_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_conversation_owner_type UNIQUE (owner_surface_type, owner_surface_id, conversation_type)
);

CREATE INDEX idx_im_conversation_context_status ON im_conversation (context_type, context_id, status);
CREATE INDEX idx_im_conversation_last_active_at ON im_conversation (last_active_at);

CREATE TABLE im_conversation_member (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_seq BIGINT NOT NULL DEFAULT 0,
    delivery_mode VARCHAR(32) NOT NULL,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_conversation_member_user UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_im_conversation_member_user_status ON im_conversation_member (user_id, status);

CREATE TABLE im_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    message_seq BIGINT NOT NULL,
    client_msg_id VARCHAR(128),
    message_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    payload_json JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_message_conversation_seq UNIQUE (conversation_id, message_seq)
);

CREATE INDEX idx_im_message_conversation_sent_at ON im_message (conversation_id, sent_at);

CREATE TABLE im_outbox (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    message_seq BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_im_outbox_dispatch ON im_outbox (status, available_at);

CREATE TABLE im_inbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    message_seq BIGINT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_inbox_user_conversation_message UNIQUE (user_id, conversation_id, message_id)
);

CREATE INDEX idx_im_inbox_user_seq ON im_inbox (user_id, message_seq);

CREATE TABLE im_global_mute (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE im_dm_block (
    id BIGSERIAL PRIMARY KEY,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(512),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE im_ban (
    id BIGSERIAL PRIMARY KEY,
    surface_type VARCHAR(32) NOT NULL,
    surface_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reason VARCHAR(512),
    expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_im_ban_surface_status ON im_ban (surface_type, surface_id, status);
CREATE INDEX idx_im_ban_user_status ON im_ban (user_id, status);

CREATE TABLE im_ws_ticket (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    roles_json JSONB NOT NULL DEFAULT '[]',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_ws_ticket_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_im_ws_ticket_user_status ON im_ws_ticket (user_id, status);
