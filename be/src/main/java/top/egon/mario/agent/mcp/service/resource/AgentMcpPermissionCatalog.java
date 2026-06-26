package top.egon.mario.agent.mcp.service.resource;

import java.util.List;

/**
 * Permission codes for managed MCP admin resources.
 */
public final class AgentMcpPermissionCatalog {

    public static final String APP_CODE = "agent";

    public static final String MENU_AGENT = "menu:agent";
    public static final String MENU_SERVERS = "menu:agent:mcp-servers";
    public static final String MENU_LOGS = "menu:agent:mcp-logs";

    public static final String BTN_SERVER_ADD = "btn:agent:mcp-server:add";
    public static final String BTN_SERVER_EDIT = "btn:agent:mcp-server:edit";
    public static final String BTN_SERVER_DELETE = "btn:agent:mcp-server:delete";
    public static final String BTN_SERVER_TEST = "btn:agent:mcp-server:test";
    public static final String BTN_SERVER_DISCOVER = "btn:agent:mcp-server:discover";
    public static final String BTN_SERVER_TOGGLE = "btn:agent:mcp-server:toggle";
    public static final String BTN_TOOL_EDIT_POLICY = "btn:agent:mcp-tool:edit-policy";
    public static final String BTN_TOOL_TOGGLE = "btn:agent:mcp-tool:toggle";
    public static final String BTN_LOG_VIEW = "btn:agent:mcp-log:view";

    public static final String API_SERVER_COLLECTION = "api:agent:mcp-server:collection";
    public static final String API_SERVER_ALL = "api:agent:mcp-server:*";
    public static final String API_TOOL_COLLECTION = "api:agent:mcp-tool:collection";
    public static final String API_TOOL_ALL = "api:agent:mcp-tool:*";
    public static final String API_LOG_COLLECTION = "api:agent:mcp-log:collection";
    public static final String API_LOG_ALL = "api:agent:mcp-log:*";
    public static final String API_RBAC_AUTH_SELF = "api:rbac:auth:self";
    public static final String API_RBAC_ME_SELF = "api:rbac:me:self";

    public static final String ROLE_USER = "AGENT_MCP_USER";
    public static final String ROLE_ADMIN = "AGENT_MCP_ADMIN";

    public static final List<String> USER_PERMISSION_CODES = List.of(
            MENU_AGENT,
            MENU_SERVERS,
            BTN_SERVER_ADD,
            BTN_SERVER_EDIT,
            BTN_SERVER_DELETE,
            BTN_SERVER_TEST,
            BTN_SERVER_DISCOVER,
            BTN_SERVER_TOGGLE,
            BTN_TOOL_EDIT_POLICY,
            BTN_TOOL_TOGGLE,
            API_SERVER_COLLECTION,
            API_SERVER_ALL,
            API_TOOL_COLLECTION,
            API_TOOL_ALL,
            API_RBAC_AUTH_SELF,
            API_RBAC_ME_SELF
    );

    public static final List<String> ADMIN_PERMISSION_CODES = List.of(
            MENU_AGENT,
            MENU_SERVERS,
            MENU_LOGS,
            BTN_SERVER_ADD,
            BTN_SERVER_EDIT,
            BTN_SERVER_DELETE,
            BTN_SERVER_TEST,
            BTN_SERVER_DISCOVER,
            BTN_SERVER_TOGGLE,
            BTN_TOOL_EDIT_POLICY,
            BTN_TOOL_TOGGLE,
            BTN_LOG_VIEW,
            API_SERVER_COLLECTION,
            API_SERVER_ALL,
            API_TOOL_COLLECTION,
            API_TOOL_ALL,
            API_LOG_COLLECTION,
            API_LOG_ALL,
            API_RBAC_AUTH_SELF,
            API_RBAC_ME_SELF
    );

    private AgentMcpPermissionCatalog() {
    }

}
