package top.egon.mario.agent.mcp.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacButtonSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies managed MCP resources to the RBAC synchronizer.
 */
@Component
public class AgentMcpRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = AgentMcpPermissionCatalog.APP_CODE;
    private static final List<String> SERVER_API_CODES = List.of(AgentMcpPermissionCatalog.API_SERVER_ALL);
    private static final List<String> TOOL_API_CODES = List.of(AgentMcpPermissionCatalog.API_TOOL_ALL);
    private static final List<String> LOG_API_CODES = List.of(AgentMcpPermissionCatalog.API_LOG_COLLECTION);

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(
                agentMenu(),
                menu(AgentMcpPermissionCatalog.MENU_SERVERS, "MCP 服务配置", "/agent/mcp/servers",
                        "agent-mcp-servers", 11, "ApiOutlined"),
                menu(AgentMcpPermissionCatalog.MENU_LOGS, "MCP 调用日志", "/agent/mcp/logs",
                        "agent-mcp-logs", 13, "AuditOutlined"),
                button(AgentMcpPermissionCatalog.BTN_SERVER_ADD, "新建 MCP 服务", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "create", 1, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_SERVER_EDIT, "编辑 MCP 服务", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "edit", 2, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_SERVER_DELETE, "删除 MCP 服务", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "delete", 3, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_SERVER_TEST, "测试 MCP 服务", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "test", 4, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_SERVER_DISCOVER, "发现 MCP 工具",
                        AgentMcpPermissionCatalog.MENU_SERVERS, "discover", 5, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_SERVER_TOGGLE, "启停 MCP 服务", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "toggle", 6, SERVER_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_TOOL_EDIT_POLICY, "编辑 MCP 工具策略",
                        AgentMcpPermissionCatalog.MENU_SERVERS, "editToolPolicy", 7, TOOL_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_TOOL_TOGGLE, "启停 MCP 工具", AgentMcpPermissionCatalog.MENU_SERVERS,
                        "toggleTool", 8, TOOL_API_CODES),
                button(AgentMcpPermissionCatalog.BTN_LOG_VIEW, "查看 MCP 调用日志", AgentMcpPermissionCatalog.MENU_LOGS,
                        "view", 1, LOG_API_CODES),
                api(AgentMcpPermissionCatalog.API_SERVER_COLLECTION, "MCP 服务集合", "GET",
                        "/api/admin/agent/mcp/servers", ApiMatcherType.EXACT),
                api(AgentMcpPermissionCatalog.API_SERVER_ALL, "MCP 服务管理", "ANY",
                        "/api/admin/agent/mcp/servers/**", ApiMatcherType.ANT),
                api(AgentMcpPermissionCatalog.API_TOOL_COLLECTION, "MCP 工具集合", "GET",
                        "/api/admin/agent/mcp/tools", ApiMatcherType.EXACT),
                api(AgentMcpPermissionCatalog.API_TOOL_ALL, "MCP 工具管理", "ANY",
                        "/api/admin/agent/mcp/tools/**", ApiMatcherType.ANT),
                api(AgentMcpPermissionCatalog.API_LOG_COLLECTION, "MCP 调用日志集合", "GET",
                        "/api/admin/agent/mcp/tool-calls", ApiMatcherType.EXACT),
                api(AgentMcpPermissionCatalog.API_LOG_ALL, "MCP 调用日志管理", "ANY",
                        "/api/admin/agent/mcp/tool-calls/**", ApiMatcherType.ANT)
        );
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(
                        APP_CODE,
                        AgentMcpPermissionCatalog.ROLE_USER,
                        "Agent MCP User",
                        "System role for managing MCP servers and tool policies.",
                        55,
                        AgentMcpPermissionCatalog.USER_PERMISSION_CODES,
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        AgentMcpPermissionCatalog.ROLE_ADMIN,
                        "Agent MCP Administrator",
                        "System role for managing MCP servers, tool policies and call logs.",
                        56,
                        AgentMcpPermissionCatalog.ADMIN_PERMISSION_CODES,
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed agentMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                AgentMcpPermissionCatalog.MENU_AGENT,
                "首页控制台",
                null,
                PermissionStatus.ENABLED,
                10,
                "AI model usage dashboard",
                new RbacMenuSeed("dashboard", "/dashboard", null, null, "DashboardOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed menu(String code, String name, String path, String routeName, int sortNo, String icon) {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                code,
                name,
                AgentMcpPermissionCatalog.MENU_AGENT,
                PermissionStatus.ENABLED,
                sortNo,
                name,
                new RbacMenuSeed(routeName, path, null, null, icon, false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed button(String code, String name, String menuCode, String action, int sortNo,
                                    List<String> apiCodes) {
        return RbacResourceSeed.button(
                APP_CODE,
                APP_CODE,
                code,
                name,
                menuCode,
                PermissionStatus.ENABLED,
                sortNo,
                name,
                new RbacButtonSeed(action, action, null),
                apiCodes,
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed api(String code, String name, String method, String pattern, ApiMatcherType matcherType) {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                code,
                name,
                PermissionStatus.ENABLED,
                0,
                name,
                new RbacApiSeed(method, pattern, matcherType, false, ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

}
