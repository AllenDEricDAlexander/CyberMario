package top.egon.mario.agent.service.resource;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionStatus;
import top.egon.mario.rbac.service.resource.RbacResourceProvider;
import top.egon.mario.rbac.service.resource.model.RbacApiSeed;
import top.egon.mario.rbac.service.resource.model.RbacMenuSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSeed;
import top.egon.mario.rbac.service.resource.model.RbacResourceSource;
import top.egon.mario.rbac.service.resource.model.RbacRolePresetSeed;

import java.util.List;

/**
 * Supplies Agent debug and conversation audit resources to the RBAC synchronizer.
 */
@Component
public class AgentDebugRbacResourceProvider implements RbacResourceProvider {

    private static final String APP_CODE = "agent";

    @Override
    public String appCode() {
        return APP_CODE;
    }

    @Override
    public List<RbacResourceSeed> resources() {
        return List.of(
                debugMenu(),
                conversationAuditMenu(),
                runAuditMenu(),
                debugChatStreamApi(),
                presetCollectionApi(),
                presetApi(),
                conversationAuditCollectionApi(),
                conversationAuditApi(),
                runAuditCollectionApi(),
                runAuditApi()
        );
    }

    @Override
    public List<RbacRolePresetSeed> rolePresets() {
        return List.of(new RbacRolePresetSeed(
                APP_CODE,
                "CHAT_BASIC",
                "Chat Basic User",
                "System role for the basic agent chat console.",
                50,
                List.of("menu:agent:debug",
                        "api:agent:debug:chat:stream",
                        "api:agent:preset:collection",
                        "api:agent:preset:*"),
                RbacResourceSource.PROVIDER
        ));
    }

    private RbacResourceSeed debugMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                "menu:agent:debug",
                "Agent 调试",
                null,
                PermissionStatus.ENABLED,
                20,
                "Agent debug workspace",
                new RbacMenuSeed("agentDebug", "/agent/debug", null, null, "ExperimentOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed conversationAuditMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                "menu:agent:conversation-audit",
                "对话审计",
                null,
                PermissionStatus.ENABLED,
                90,
                "Super-admin-only agent conversation audit",
                new RbacMenuSeed("agentConversationAudits", "/agent/conversation-audits", null, null,
                        "AuditOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed runAuditMenu() {
        return RbacResourceSeed.menu(
                APP_CODE,
                APP_CODE,
                "menu:agent:run-audit",
                "运行审计",
                null,
                PermissionStatus.ENABLED,
                95,
                "Super-admin-only unified agent run audit",
                new RbacMenuSeed("agentRunAudits", "/agent/run-audits", null, null,
                        "NodeIndexOutlined", false, true, null),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed debugChatStreamApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:debug:chat:stream",
                "Agent 调试流式对话",
                PermissionStatus.ENABLED,
                0,
                "Stream chat endpoint for agent debug presets",
                new RbacApiSeed("POST", "/api/agent/debug/chat/stream", ApiMatcherType.EXACT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed presetCollectionApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:preset:collection",
                "Agent 预设集合",
                PermissionStatus.ENABLED,
                0,
                "Agent debug preset collection endpoints",
                new RbacApiSeed("GET", "/api/agent/presets", ApiMatcherType.EXACT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed presetApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:preset:*",
                "Agent 预设管理",
                PermissionStatus.ENABLED,
                0,
                "Agent debug preset management endpoints",
                new RbacApiSeed("ANY", "/api/agent/presets/**", ApiMatcherType.ANT, false, ApiRiskLevel.MEDIUM),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed conversationAuditCollectionApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:conversation-audit:collection",
                "Agent 对话审计集合",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only agent conversation audit collection",
                new RbacApiSeed("GET", "/api/admin/agent/conversation-audits", ApiMatcherType.EXACT, false,
                        ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed conversationAuditApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:conversation-audit:*",
                "Agent 对话审计管理",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only agent conversation audit detail endpoints",
                new RbacApiSeed("ANY", "/api/admin/agent/conversation-audits/**", ApiMatcherType.ANT, false,
                        ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed runAuditCollectionApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:run-audit:collection",
                "Agent 运行审计集合",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only unified agent run audit collection",
                new RbacApiSeed("GET", "/api/admin/agent/run-audits", ApiMatcherType.EXACT, false,
                        ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

    private RbacResourceSeed runAuditApi() {
        return RbacResourceSeed.api(
                APP_CODE,
                APP_CODE,
                "api:agent:run-audit:*",
                "Agent 运行审计管理",
                PermissionStatus.ENABLED,
                0,
                "Super-admin-only unified agent run audit detail endpoints",
                new RbacApiSeed("ANY", "/api/admin/agent/run-audits/**", ApiMatcherType.ANT, false,
                        ApiRiskLevel.HIGH),
                RbacResourceSource.PROVIDER
        );
    }

}
