-- V41: add platform friendship and directed contact state.

CREATE TABLE im_friendship (
    id BIGSERIAL PRIMARY KEY,
    user_lo_id BIGINT NOT NULL,
    user_hi_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_message VARCHAR(512) NOT NULL DEFAULT '',
    decided_by BIGINT,
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_reason VARCHAR(512),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP WITH TIME ZONE,
    removed_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_friendship_users UNIQUE (user_lo_id, user_hi_id),
    CONSTRAINT chk_im_friendship_ordered CHECK (user_lo_id < user_hi_id)
);

CREATE INDEX idx_im_friendship_lo_status ON im_friendship (user_lo_id, status);
CREATE INDEX idx_im_friendship_hi_status ON im_friendship (user_hi_id, status);
CREATE INDEX idx_im_friendship_requester_status ON im_friendship (requester_user_id, status);

CREATE TABLE im_contact (
    id BIGSERIAL PRIMARY KEY,
    friendship_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    contact_user_id BIGINT NOT NULL,
    remark VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_contact_owner_user UNIQUE (owner_user_id, contact_user_id),
    CONSTRAINT chk_im_contact_not_self CHECK (owner_user_id <> contact_user_id)
);

CREATE INDEX idx_im_contact_friendship ON im_contact (friendship_id);
CREATE INDEX idx_im_contact_owner_status ON im_contact (owner_user_id, status);
