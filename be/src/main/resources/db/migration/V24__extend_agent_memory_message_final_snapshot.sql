ALTER TABLE agent_memory_message ADD COLUMN message_status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED';
ALTER TABLE agent_memory_message ADD COLUMN error_code VARCHAR(256);
ALTER TABLE agent_memory_message ADD COLUMN error_message TEXT;
ALTER TABLE agent_memory_message ADD COLUMN metadata_json TEXT;
