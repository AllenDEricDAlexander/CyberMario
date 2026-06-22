ALTER TABLE agent_memory_message
    ADD COLUMN message_status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN error_code VARCHAR(256),
    ADD COLUMN error_message TEXT,
    ADD COLUMN metadata_json TEXT;
