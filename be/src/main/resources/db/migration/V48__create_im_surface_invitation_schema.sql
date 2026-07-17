-- V47: add invitation state for user-created channels and groups.

CREATE TABLE im_surface_invitation (
    id BIGSERIAL PRIMARY KEY,
    surface_type VARCHAR(32) NOT NULL,
    surface_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NOT NULL DEFAULT '',
    responded_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_im_surface_invitation_target UNIQUE (surface_type, surface_id, invitee_user_id),
    CONSTRAINT chk_im_surface_invitation_type CHECK (surface_type IN ('CHANNEL', 'GROUP')),
    CONSTRAINT chk_im_surface_invitation_not_self CHECK (inviter_user_id <> invitee_user_id)
);

CREATE INDEX idx_im_surface_invitation_invitee_status
    ON im_surface_invitation (invitee_user_id, status, created_at);
CREATE INDEX idx_im_surface_invitation_surface_status
    ON im_surface_invitation (surface_type, surface_id, status);
