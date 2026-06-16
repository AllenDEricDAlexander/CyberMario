package top.egon.mario.agent.model.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.agent.model.dto.enums.ModelAuditDashboardScope;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;
import top.egon.mario.agent.model.dto.enums.ModelScenario;
import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditDimensionStatResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.agent.model.po.ModelAuditPo;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.po.enums.TokenUsageSource;
import top.egon.mario.agent.model.repository.ModelAuditRepository;
import top.egon.mario.rag.repository.RagKnowledgeBaseUserRepository;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies dashboard aggregation keeps global audit data behind administrator permissions.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.rbac.resource-sync.enabled=false",
        "mario.rbac.role-presets.enabled=false",
        "mario.rbac.bootstrap.admin.enabled=false"
})
class ModelAuditDashboardServiceTests {

    private static final Instant BASE_TIME = Instant.parse("2026-06-14T00:00:00Z");

    @Autowired
    private ModelAuditDashboardService dashboardService;
    @Autowired
    private ModelAuditRepository auditRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RagKnowledgeBaseUserRepository knowledgeBaseUserRepository;

    private UserPo mario;
    private UserPo luigi;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        knowledgeBaseUserRepository.deleteAll();
        userRepository.deleteAll();
        mario = userRepository.save(user("mario", "Mario"));
        luigi = userRepository.save(user("luigi", "Luigi"));
    }

    @Test
    void selfSummaryOnlyAggregatesCurrentUserAndDoesNotExposeUserStats() {
        audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
        audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.FAILED, 3, 0, 3, 40);
        audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);

        ModelAuditDashboardSummaryResponse response = dashboardService.selfSummary(query(null), selfPrincipal(mario.getId()));

        assertThat(response.scope()).isEqualTo(ModelAuditDashboardScope.SELF);
        assertThat(response.overview().callCount()).isEqualTo(2L);
        assertThat(response.overview().successCount()).isEqualTo(1L);
        assertThat(response.overview().failedCount()).isEqualTo(1L);
        assertThat(response.overview().totalTokens()).isEqualTo(23L);
        assertThat(response.userStats()).isEmpty();
        assertThat(response.modelStats()).extracting(ModelAuditDimensionStatResponse::name).containsExactly("qwen-plus");
    }

    @Test
    void selfRecentCallsArePagedAndScopedToCurrentUser() {
        audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 1, 1, 2, 20);
        audit(mario.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 2, 2, 4, 40);
        audit(mario.getId(), "qwen-turbo", ModelScenario.BACKGROUND_TASK, ModelAuditStatus.FAILED, 3, 3, 6, 60);
        audit(luigi.getId(), "qwen-leaked", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 20, 20, 40, 90);

        Page<ModelAuditRecentCallResponse> page = dashboardService.selfRecentCalls(
                query(null),
                PageRequest.of(0, 2),
                selfPrincipal(mario.getId()));

        assertThat(page.getTotalElements()).isEqualTo(3L);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent())
                .extracting(ModelAuditRecentCallResponse::model)
                .containsExactly("qwen-turbo", "qwen-max");
        assertThat(page.getContent())
                .extracting(ModelAuditRecentCallResponse::userId)
                .containsOnly(mario.getId());
    }

    @Test
    void selfRecentCallsRejectForeignUserFilter() {
        assertThatThrownBy(() -> dashboardService.selfRecentCalls(
                query(luigi.getId()),
                PageRequest.of(0, 20),
                selfPrincipal(mario.getId())))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
    }

    @Test
    void globalSummaryRequiresAdministratorAuthority() {
        assertThatThrownBy(() -> dashboardService.globalSummary(query(null), selfPrincipal(mario.getId())))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
    }

    @Test
    void globalRecentCallsRequiresAdministratorAuthority() {
        assertThatThrownBy(() -> dashboardService.globalRecentCalls(
                query(null),
                PageRequest.of(0, 20),
                selfPrincipal(mario.getId())))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");
    }

    @Test
    void globalSummaryAllowsAdministratorUserFilter() {
        audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
        audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);

        ModelAuditDashboardSummaryResponse response = dashboardService.globalSummary(query(luigi.getId()), adminPrincipal());

        assertThat(response.scope()).isEqualTo(ModelAuditDashboardScope.GLOBAL);
        assertThat(response.overview().callCount()).isEqualTo(1L);
        assertThat(response.overview().totalTokens()).isEqualTo(40L);
        assertThat(response.userStats()).hasSize(1);
        assertThat(response.userStats().getFirst().userId()).isEqualTo(luigi.getId());
        assertThat(response.userStats().getFirst().username()).isEqualTo("luigi");
    }

    @Test
    void globalRecentCallsReturnRequestedUserPage() {
        audit(mario.getId(), "qwen-plus", ModelScenario.AGENT_CHAT, ModelAuditStatus.SUCCESS, 12, 8, 20, 120);
        audit(luigi.getId(), "qwen-max", ModelScenario.RAG_CHAT, ModelAuditStatus.SUCCESS, 30, 10, 40, 90);
        audit(luigi.getId(), "qwen-turbo", ModelScenario.RAG_SUMMARY, ModelAuditStatus.FAILED, 30, 30, 60, 35);

        Page<ModelAuditRecentCallResponse> page = dashboardService.globalRecentCalls(
                query(luigi.getId()),
                PageRequest.of(0, 10),
                adminPrincipal());

        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getContent())
                .extracting(ModelAuditRecentCallResponse::model)
                .containsExactly("qwen-turbo", "qwen-max");
        assertThat(page.getContent())
                .extracting(ModelAuditRecentCallResponse::username)
                .containsOnly("luigi");
    }

    @Test
    void userOptionsRequireGlobalAccessAndReturnIdNameLabels() {
        assertThatThrownBy(() -> dashboardService.userOptions("lu", 20, selfPrincipal(mario.getId())))
                .isInstanceOf(RbacException.class)
                .hasMessageContaining("MODEL_AUDIT_DASHBOARD_FORBIDDEN");

        assertThat(dashboardService.userOptions("lu", 20, adminPrincipal()))
                .extracting(ModelAuditUserOptionResponse::id)
                .containsExactly(luigi.getId());
        assertThat(dashboardService.userOptions(String.valueOf(mario.getId()), 20, adminPrincipal()))
                .extracting(ModelAuditUserOptionResponse::username)
                .containsExactly("mario");
    }

    private ModelAuditDashboardQuery query(Long userId) {
        return new ModelAuditDashboardQuery(
                BASE_TIME.minusSeconds(3600),
                BASE_TIME.plusSeconds(86400),
                userId,
                null,
                null,
                null,
                null
        );
    }

    private UserPo user(String username, String nickname) {
        UserPo user = new UserPo();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash("{noop}password");
        return user;
    }

    private void audit(Long userId, String model, ModelScenario scenario, ModelAuditStatus status,
                       int promptTokens, int completionTokens, int totalTokens, long durationMs) {
        ModelAuditPo audit = new ModelAuditPo();
        audit.setRequestId("request-" + userId + "-" + totalTokens);
        audit.setTraceId("trace-" + userId + "-" + totalTokens);
        audit.setUserId(userId);
        audit.setScenario(scenario);
        audit.setProvider(ModelProviderType.DASHSCOPE);
        audit.setModel(model);
        audit.setPromptTokens(promptTokens);
        audit.setCompletionTokens(completionTokens);
        audit.setTotalTokens(totalTokens);
        audit.setTokenUsageSource(TokenUsageSource.PROVIDER);
        audit.setStreaming(false);
        audit.setStatus(status);
        audit.setStartedAt(BASE_TIME);
        audit.setFinishedAt(BASE_TIME.plusMillis(durationMs));
        audit.setDurationMs(durationMs);
        audit.setPromptChars(promptTokens);
        audit.setCompletionChars(completionTokens);
        audit.setCreatedAt(BASE_TIME.plusSeconds(totalTokens));
        auditRepository.save(audit);
    }

    private RbacPrincipal selfPrincipal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("CHAT_USER"),
                Set.of("api:agent:model-audit:dashboard:self"), "v1");
    }

    private RbacPrincipal adminPrincipal() {
        return new RbacPrincipal(1L, "admin", Set.of("RBAC_ADMIN"),
                Set.of("api:agent:model-audit:dashboard:global",
                        "api:agent:model-audit:dashboard:user-options"), "v1");
    }

}
