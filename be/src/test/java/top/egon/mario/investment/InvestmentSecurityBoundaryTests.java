package top.egon.mario.investment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import top.egon.mario.investment.agent.repository.InvestmentAgentRunRepository;
import top.egon.mario.investment.agent.service.InvestmentAgentToolCallbackFactory;
import top.egon.mario.investment.bootstrap.InvestmentRbacResourceProvider;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.quant.repository.InvestmentBacktestRunRepository;
import top.egon.mario.investment.quant.web.dto.SubmitInvestmentBacktestRequest;
import top.egon.mario.investment.research.repository.InvestmentResearchReportRepository;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;
import top.egon.mario.rbac.po.enums.PermissionType;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks authentication, ownership, platform separation and paper-only API boundaries.
 */
class InvestmentSecurityBoundaryTests {

    @Test
    void everyInvestmentApiRequiresAuthenticationAndPlatformRoleHasNoPrivatePermissions() {
        InvestmentRbacResourceProvider provider = new InvestmentRbacResourceProvider();

        assertThat(provider.resources())
                .filteredOn(resource -> resource.type() == PermissionType.API)
                .allSatisfy(resource -> assertThat(resource.api().publicFlag()).isFalse());
        assertThat(provider.rolePresets())
                .filteredOn(role -> "INVESTMENT_PLATFORM_ADMIN".equals(role.roleCode()))
                .singleElement()
                .satisfies(role -> assertThat(role.permissionCodes())
                        .contains("api:investment:platform:*", "api:investment:market:*")
                        .doesNotContain("api:investment:workspace:*", "api:investment:private-detail:*",
                                "button:investment:paper:trade", "button:investment:agent:run"));
    }

    @Test
    void ownerGateRejectsCrossWorkspaceAccountAndRunEvenForAPrivilegedCaller() {
        InvestmentWorkspaceRepository workspaceRepository = mock(InvestmentWorkspaceRepository.class);
        InvestmentWatchlistRepository watchlistRepository = mock(InvestmentWatchlistRepository.class);
        InvestmentPaperAccountRepository accountRepository = mock(InvestmentPaperAccountRepository.class);
        InvestmentAgentRunRepository runRepository = mock(InvestmentAgentRunRepository.class);
        InvestmentAccessService access = new InvestmentAccessService(
                workspaceRepository, watchlistRepository, accountRepository, runRepository);
        when(workspaceRepository.existsOwnedActiveWorkspace(7L, 999L)).thenReturn(false);
        when(accountRepository.findOwnedAccount(31L, 999L)).thenReturn(Optional.empty());
        when(runRepository.findOwnedRun(41L, 999L)).thenReturn(Optional.empty());

        assertForbidden(() -> access.requireWorkspaceOwner(7L, 999L));
        assertForbidden(() -> access.requireAccountOwner(31L, 999L));
        assertForbidden(() -> access.requireAgentRunOwner(41L, 999L));
    }

    @Test
    void reportBacktestAndAgentDetailsExposeOwnerScopedRepositoryQueries() throws Exception {
        assertOwnerScopedMethod(InvestmentResearchReportRepository.class,
                "findOwnedReport", Long.class, Long.class);
        assertOwnerScopedMethod(InvestmentBacktestRunRepository.class,
                "findOwnedRun", Long.class, Long.class);
        assertOwnerScopedMethod(InvestmentAgentRunRepository.class,
                "findOwnedRun", Long.class, Long.class);
    }

    @Test
    void strategyRequestRejectsClientParametersSourcesAndInitialEquity() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        for (String forbiddenField : List.of(
                "\"parameters\":{\"fast\":5}", "\"sourceId\":3", "\"initialEquity\":\"10000\"")) {
            String json = """
                    {"strategyCode":"FIXED","instrumentIds":[11],
                     "startTime":"2035-01-01T00:00:00Z","endTime":"2035-01-02T00:00:00Z",%s}
                    """.formatted(forbiddenField);
            assertThatThrownBy(() -> mapper.readValue(json, SubmitInvestmentBacktestRequest.class))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void investmentHasNoRealTradingEndpointCredentialContractOrGlobalToolBean() throws Exception {
        Path root = Path.of("src/main/java/top/egon/mario/investment");
        String source = readJavaSources(root).toLowerCase(java.util.Locale.ROOT);

        assertThat(source)
                .doesNotContain("/api/investment/live")
                .doesNotContain("/api/investment/real")
                .doesNotContain("exchangecredential")
                .doesNotContain("bitgetsecret")
                .doesNotContain("privateclient");
        assertThat(List.of(InvestmentAgentToolCallbackFactory.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(Bean.class));
    }

    private void assertOwnerScopedMethod(Class<?> repository, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = repository.getMethod(name, parameterTypes);
        assertThat(method.getName()).startsWith("findOwned");
        assertThat(method.getParameterCount()).isEqualTo(2);
    }

    private void assertForbidden(ThrowingAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvestmentException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(InvestmentErrorCode.FORBIDDEN));
    }

    private String readJavaSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            StringBuilder source = new StringBuilder();
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(path)).append('\n');
            }
            return source.toString();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
