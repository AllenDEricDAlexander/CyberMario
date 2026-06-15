CREATE UNIQUE INDEX uk_agent_mcp_server_code_active
    ON agent_mcp_server_config (server_code)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_agent_mcp_tool_server_name_active
    ON agent_mcp_tool_config (server_id, tool_name)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_agent_mcp_tool_key_active
    ON agent_mcp_tool_config (tool_key)
    WHERE deleted = FALSE;
