package top.egon.mario.agent.service.resource;

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
 * Supplies current-user Agent memory resources to the RBAC synchronizer.
 */
@Component
public class AgentMemoryRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = AgentMemoryPermissionCatalog.APP_CODE;
    private static final List<String> SESSION_API_CODES = List.of(
            AgentMemoryPermissionCatalog.API_SESSION_COLLECTION,
            AgentMemoryPermissionCatalog.API_SESSION_ALL
    );

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(
                memoryMenu(),
                archiveMenu(),
                button(AgentMemoryPermissionCatalog.BTN_SWITCH, "记忆开关",
                        AgentMemoryPermissionCatalog.MENU_MEMORY, "switch", 1, SESSION_API_CODES),
                button(AgentMemoryPermissionCatalog.BTN_RELEASE, "释放会话",
                        AgentMemoryPermissionCatalog.MENU_MEMORY, "release", 2, SESSION_API_CODES),
                button(AgentMemoryPermissionCatalog.BTN_ARCHIVE, "归档会话",
                        AgentMemoryPermissionCatalog.MENU_MEMORY, "archive", 3, SESSION_API_CODES),
                button(AgentMemoryPermissionCatalog.BTN_RESTORE, "恢复会话",
                        AgentMemoryPermissionCatalog.MENU_MEMORY_ARCHIVE, "restore", 1, SESSION_API_CODES),
                button(AgentMemoryPermissionCatalog.BTN_DELETE, "删除归档会话",
                        AgentMemoryPermissionCatalog.MENU_MEMORY_ARCHIVE, "delete", 2, SESSION_API_CODES),
                api(AgentMemoryPermissionCatalog.API_SESSION_COLLECTION, "Agent 记忆会话集合",
                        "ANY", "/api/agent/memory/sessions", ApiMatcherType.EXACT),
                api(AgentMemoryPermissionCatalog.API_SESSION_ALL, "Agent 记忆会话管理",
                        "ANY", "/api/agent/memory/sessions/**", ApiMatcherType.ANT),
                api(AgentMemoryPermissionCatalog.API_MESSAGE_READ, "Agent 记忆消息读取",
                        "GET", "/api/agent/memory/sessions/*/messages", ApiMatcherType.ANT),
                api(AgentMemoryPermissionCatalog.API_LONG_TERM_READ, "Agent 长期记忆读取",
                        "GET", "/api/agent/memory/long-term", ApiMatcherType.EXACT),
                api(AgentMemoryPermissionCatalog.API_LONG_TERM_VERSION, "Agent 长期记忆版本",
                        "GET", "/api/agent/memory/long-term/versions/**", ApiMatcherType.ANT),
                api(AgentMemoryPermissionCatalog.API_EXTRACTION_READ, "Agent 记忆提取审计",
                        "GET", "/api/agent/memory/extractions", ApiMatcherType.EXACT)
        );
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(
                new RbacRolePresetSeed(
                        APP_CODE,
                        AgentMemoryPermissionCatalog.ROLE_CHAT_BASIC,
                        "Chat Basic User",
                        "System role for the basic agent chat console.",
                        50,
                        AgentMemoryPermissionCatalog.CHAT_BASIC_PERMISSION_CODES,
                        RbacResourceSource.PROVIDER
                ),
                new RbacRolePresetSeed(
                        APP_CODE,
                        AgentMemoryPermissionCatalog.ROLE_RAG_USER,
                        "RAG User",
                        "System role for default RAG knowledge base usage.",
                        40,
                        AgentMemoryPermissionCatalog.RAG_USER_PERMISSION_CODES,
                        RbacResourceSource.PROVIDER
                )
        );
    }

    private RbacResourceSeed memoryMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                AgentMemoryPermissionCatalog.MENU_MEMORY,
                "记忆管理",
                null,
                PermissionStatus.ENABLED,
                30,
                "Current-user Agent memory management",
                new RbacMenuSeed("agentMemory", "/agent/memory", null, null,
                        "ProfileOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed archiveMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                AgentMemoryPermissionCatalog.MENU_MEMORY_ARCHIVE,
                "归档记忆",
                null,
                PermissionStatus.ENABLED,
                31,
                "Current-user archived Agent memory sessions",
                new RbacMenuSeed("agentMemoryArchive", "/agent/memory/archive", null, null,
                        "InboxOutlined", false, true, null),
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
                new RbacApiSeed(method, pattern, matcherType, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }
}
