CREATE UNIQUE INDEX IF NOT EXISTS uk_im_channel_global_context_key
    ON im_channel (context_type, channel_key)
    WHERE context_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_group_standalone_context_key
    ON im_group (context_type, context_id, group_key)
    WHERE channel_id IS NULL AND context_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_group_standalone_global_key
    ON im_group (context_type, group_key)
    WHERE channel_id IS NULL AND context_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_join_request_pending_surface_user
    ON im_join_request (surface_type, surface_id, user_id)
    WHERE status = 'PENDING' AND deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_message_client_msg
    ON im_message (conversation_id, sender_user_id, client_msg_id)
    WHERE client_msg_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_im_global_mute_active_lookup
    ON im_global_mute (user_id, scope_type, scope_id, expires_at)
    WHERE status = 'ACTIVE' AND deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_im_dm_block_active
    ON im_dm_block (blocker_user_id, blocked_user_id)
    WHERE status = 'ACTIVE' AND deleted = FALSE;
