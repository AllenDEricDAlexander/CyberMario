CREATE TABLE agent_mcp_server_config
(
    id                 BIGSERIAL PRIMARY KEY,
    server_code        VARCHAR(64)  NOT NULL,
    server_name        VARCHAR(128) NOT NULL,
    transport_type     VARCHAR(32)  NOT NULL,
    base_url           VARCHAR(512) NOT NULL,
    endpoint           VARCHAR(256) NOT NULL,
    headers_json       TEXT,
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    connect_timeout_ms INTEGER      NOT NULL DEFAULT 5000,
    request_timeout_ms INTEGER      NOT NULL DEFAULT 30000,
    status             VARCHAR(32)  NOT NULL DEFAULT 'DISABLED',
    last_error         VARCHAR(1024),
    last_connected_at  TIMESTAMP(6),
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT,
    updated_by         BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_agent_mcp_server_enabled ON agent_mcp_server_config (enabled, deleted);

CREATE TABLE agent_mcp_tool_config
(
    id                 BIGSERIAL PRIMARY KEY,
    server_id          BIGINT       NOT NULL,
    tool_name          VARCHAR(128) NOT NULL,
    tool_key           VARCHAR(192) NOT NULL,
    display_name       VARCHAR(192) NOT NULL,
    description        TEXT,
    input_schema_json  TEXT,
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    risk_level         VARCHAR(32)  NOT NULL DEFAULT 'MEDIUM',
    readonly           BOOLEAN      NOT NULL DEFAULT FALSE,
    require_confirm    BOOLEAN      NOT NULL DEFAULT TRUE,
    last_discovered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT,
    updated_by         BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_agent_mcp_tool_server FOREIGN KEY (server_id) REFERENCES agent_mcp_server_config (id)
);

CREATE INDEX idx_agent_mcp_tool_enabled ON agent_mcp_tool_config (enabled, deleted);
CREATE INDEX idx_agent_mcp_tool_server ON agent_mcp_tool_config (server_id, deleted);

CREATE TABLE agent_mcp_tool_call_log
(
    id                   BIGSERIAL PRIMARY KEY,
    trace_id             VARCHAR(64),
    thread_id            VARCHAR(128),
    user_id              BIGINT,
    server_code          VARCHAR(64)  NOT NULL,
    tool_key             VARCHAR(192) NOT NULL,
    tool_name            VARCHAR(128) NOT NULL,
    request_args_summary TEXT,
    response_summary     TEXT,
    status               VARCHAR(32)  NOT NULL,
    error_msg            VARCHAR(1024),
    cost_ms              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_mcp_tool_call_created ON agent_mcp_tool_call_log (created_at DESC);
CREATE INDEX idx_agent_mcp_tool_call_thread ON agent_mcp_tool_call_log (thread_id);
CREATE INDEX idx_agent_mcp_tool_call_tool ON agent_mcp_tool_call_log (tool_key);
