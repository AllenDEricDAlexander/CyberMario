package top.egon.mario.agent.mcp.service.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.MenuPo;
import top.egon.mario.rbac.po.PermissionPo;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;
import top.egon.mario.rbac.po.enums.PermissionType;
import top.egon.mario.rbac.repository.ApiRepository;
import top.egon.mario.rbac.repository.AuditLogRepository;
import top.egon.mario.rbac.repository.ButtonApiRepository;
import top.egon.mario.rbac.repository.ButtonRepository;
import top.egon.mario.rbac.repository.MenuRepository;
import top.egon.mario.rbac.repository.PermissionRepository;
import top.egon.mario.rbac.repository.RefreshTokenRepository;
import top.egon.mario.rbac.repository.RoleInheritanceRepository;
import top.egon.mario.rbac.repository.RolePermissionRepository;
import top.egon.mario.rbac.repository.RoleRepository;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.repository.UserRoleRepository;
import top.egon.mario.rbac.service.resource.RbacResourceSynchronizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Verifies managed MCP resources and role presets are declared for RBAC synchronization.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=false"
})
class AgentMcpRbacResourceProviderTests {

    @Autowired
    private RbacResourceSynchronizer synchronizer;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private ButtonRepository buttonRepository;
    @Autowired
    private ApiRepository apiRepository;
    @Autowired
    private ButtonApiRepository buttonApiRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleInheritanceRepository roleInheritanceRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;

    @BeforeEach
    void setUp() {
        knowledgeBaseUserRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        rolePermissionRepository.deleteAll();
        userRoleRepository.deleteAll();
        roleInheritanceRepository.deleteAll();
        buttonApiRepository.deleteAll();
        apiRepository.deleteAll();
        buttonRepository.deleteAll();
        menuRepository.deleteAll();
        permissionRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void resourcesCanSynchronizeBeforeDashboardProviderOnCleanDatabase() {
        AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

        assertThatNoException()
                .isThrownBy(() -> synchronizer.synchronize(provider.appCode(), provider.resources(), List.of()));

        PermissionPo agentMenu = permissionRepository.findByPermCodeAndDeletedFalse("menu:agent").orElseThrow();
        PermissionPo serverMenu = permissionRepository.findByPermCodeAndDeletedFalse("menu:agent:mcp-servers").orElseThrow();
        assertThat(serverMenu.getParentId()).isEqualTo(agentMenu.getId());
        assertThat(menuRepository.findById(agentMenu.getId()))
                .map(MenuPo::getRoutePath)
                .contains("/dashboard");
    }

    @Test
    void mcpUserCanManageServersAndToolsButCannotViewLogs() {
        AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

        assertThat(provider.rolePresets())
                .filteredOn(seed -> "AGENT_MCP_USER".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("menu:agent",
                                "menu:agent:mcp-servers",
                                "menu:agent:mcp-tools",
                                "btn:agent:mcp-server:add",
                                "btn:agent:mcp-server:edit",
                                "btn:agent:mcp-server:delete",
                                "btn:agent:mcp-server:test",
                                "btn:agent:mcp-server:discover",
                                "btn:agent:mcp-server:toggle",
                                "btn:agent:mcp-tool:edit-policy",
                                "btn:agent:mcp-tool:toggle",
                                "api:agent:mcp-server:collection",
                                "api:agent:mcp-server:*",
                                "api:agent:mcp-tool:collection",
                                "api:agent:mcp-tool:*",
                                "api:rbac:auth:self",
                                "api:rbac:me:self")
                        .doesNotContain("menu:agent:mcp-logs",
                                "btn:agent:mcp-log:view",
                                "api:agent:mcp-log:collection",
                                "api:agent:mcp-log:*"));
    }

    @Test
    void mcpAdminCanViewLogs() {
        AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

        assertThat(provider.rolePresets())
                .filteredOn(seed -> "AGENT_MCP_ADMIN".equals(seed.roleCode()))
                .singleElement()
                .satisfies(seed -> assertThat(seed.permissionCodes())
                        .contains("menu:agent:mcp-logs",
                                "btn:agent:mcp-log:view",
                                "api:agent:mcp-log:collection",
                                "api:agent:mcp-log:*"));
    }

    @Test
    void resourceApisContainExpectedPathsMatchersAndRisk() {
        AgentMcpRbacResourceProvider provider = new AgentMcpRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.API)
                .filteredOn(seed -> "api:agent:mcp-server:collection".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.api().httpMethod()).isEqualTo("GET");
                    assertThat(seed.api().urlPattern()).isEqualTo("/api/admin/agent/mcp/servers");
                    assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.EXACT);
                    assertThat(seed.api().riskLevel()).isEqualTo(ApiRiskLevel.HIGH);
                });
        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.API)
                .filteredOn(seed -> "api:agent:mcp-tool:*".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.api().httpMethod()).isEqualTo("ANY");
                    assertThat(seed.api().urlPattern()).isEqualTo("/api/admin/agent/mcp/tools/**");
                    assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.ANT);
                    assertThat(seed.api().riskLevel()).isEqualTo(ApiRiskLevel.HIGH);
                });
        assertThat(provider.resources())
                .filteredOn(seed -> seed.type() == PermissionType.API)
                .filteredOn(seed -> "api:agent:mcp-log:collection".equals(seed.code()))
                .singleElement()
                .satisfies(seed -> {
                    assertThat(seed.api().httpMethod()).isEqualTo("GET");
                    assertThat(seed.api().urlPattern()).isEqualTo("/api/admin/agent/mcp/tool-calls");
                    assertThat(seed.api().matcherType()).isEqualTo(ApiMatcherType.EXACT);
                    assertThat(seed.api().riskLevel()).isEqualTo(ApiRiskLevel.HIGH);
                });
    }

}
