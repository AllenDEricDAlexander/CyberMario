package top.egon.mario.investment.marketdata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.marketdata.query.InvestmentPlatformQueryService;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.web.InvestmentPlatformController;
import top.egon.mario.investment.marketdata.web.dto.InvestmentPlatformSubscriptionResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Locks platform administration and the read-only subscription boundary.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentPlatformControllerTests {

    @Mock
    private InvestmentPlatformQueryService queryService;

    private InvestmentPlatformController controller;
    private RbacPrincipal admin;
    private RbacPrincipal user;

    @BeforeEach
    void setUp() {
        controller = new InvestmentPlatformController(queryService);
        ReflectionTestUtils.setField(controller, "blockingScheduler", Schedulers.immediate());
        admin = new RbacPrincipal(1L, "admin", Set.of("INVESTMENT_PLATFORM_ADMIN"), Set.of(), "v1");
        user = new RbacPrincipal(2L, "user", Set.of("INVESTMENT_USER"), Set.of(), "v1");
    }

    @Test
    void platformAdminCanReadCodeSubscriptionsButNoSubscriptionWriteRouteExists() {
        InvestmentPlatformSubscriptionResponse subscription = new InvestmentPlatformSubscriptionResponse(
                "BITGET", "USDT_FUTURES", "BTCUSDT", "CODE_DECLARED", List.of("MARKET_CANDLE"),
                List.of("MARKET"), List.of("M1"), Map.of("MARKET_CANDLE", "PT1M"),
                Map.of(), Map.of("M1", "P30D"));
        when(queryService.subscriptions()).thenReturn(List.of(subscription));

        StepVerifier.create(controller.subscriptions(admin))
                .assertNext(response -> assertThat(response.data()).containsExactly(subscription))
                .verifyComplete();

        assertThat(Arrays.stream(InvestmentPlatformController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(PostMapping.class).value())))
                .noneMatch(path -> path.contains("subscription"));
    }

    @Test
    void ordinaryInvestmentUserCannotReadOrMutatePlatformOperations() {
        assertForbidden(() -> controller.subscriptions(user));
        assertForbidden(() -> controller.retryJob(9L, user));
        assertForbidden(() -> controller.resolveQualityIssue(10L, user));
        verifyNoInteractions(queryService);
    }

    @Test
    void platformAdminCanRetryFailedJobsAndResolveOpenQualityIssues() {
        StepVerifier.create(controller.retryJob(9L, admin))
                .assertNext(response -> assertThat(response.data()).isNull())
                .verifyComplete();
        StepVerifier.create(controller.resolveQualityIssue(10L, admin))
                .assertNext(response -> assertThat(response.data()).isNull())
                .verifyComplete();

        verify(queryService).retryFailedJob(9L);
        verify(queryService).resolveQualityIssue(10L, 1L);
    }

    @Test
    void freezesDataQualityRoutesAtTheDocumentedClassAndMethodMappings() throws Exception {
        RequestMapping classMapping = InvestmentPlatformController.class.getAnnotation(RequestMapping.class);
        GetMapping getMapping = InvestmentPlatformController.class
                .getDeclaredMethod("qualityIssues", String.class, String.class, int.class, int.class,
                        RbacPrincipal.class)
                .getAnnotation(GetMapping.class);
        PostMapping postMapping = InvestmentPlatformController.class
                .getDeclaredMethod("resolveQualityIssue", long.class, RbacPrincipal.class)
                .getAnnotation(PostMapping.class);

        assertThat(classMapping.value()).containsExactly("/api/investment/platform");
        assertThat(getMapping.value()).containsExactly("/data-quality-issues");
        assertThat(postMapping.value()).containsExactly("/data-quality-issues/{issueId}/resolve");

        assertThat(Arrays.stream(InvestmentPlatformController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .doesNotContain("/quality-issues");
    }

    @Test
    void realSqlSupportsDefaultAndCombinedFiltersWithoutUntypedNullParameters() {
        EmbeddedDatabase database = platformDatabase();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            seedPlatformRows(jdbc);
            InvestmentPlatformQueryService service = platformService(database);

            assertThat(service.jobs(null, null, 1, 20).records())
                    .extracting(job -> job.id()).containsExactly(2L, 1L);
            assertThat(service.jobs("failed", "bar_incremental", 1, 20).records())
                    .extracting(job -> job.id()).containsExactly(1L);
            assertThat(service.qualityIssues(null, null, 1, 20).records())
                    .extracting(issue -> issue.id()).containsExactly(12L, 11L);
            assertThat(service.qualityIssues("open", "error", 1, 20).records())
                    .extracting(issue -> issue.id()).containsExactly(11L);
        } finally {
            database.shutdown();
        }
    }

    @Test
    void retryAndResolveRealSqlEnforcePlatformStateAndRecordTheActor() {
        EmbeddedDatabase database = platformDatabase();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            seedPlatformRows(jdbc);
            InvestmentPlatformQueryService service = platformService(database);

            service.retryFailedJob(1L);
            Map<String, Object> retried = jdbc.queryForMap(
                    "select status, attempts, last_error_code from investment_job where id = 1");
            assertThat(retried).containsEntry("STATUS", "PENDING").containsEntry("ATTEMPTS", 0);
            assertThat(retried.get("LAST_ERROR_CODE")).isNull();
            assertThatThrownBy(() -> service.retryFailedJob(2L)).isInstanceOf(InvestmentException.class);
            assertThatThrownBy(() -> service.retryFailedJob(3L)).isInstanceOf(InvestmentException.class);
            assertThatThrownBy(() -> service.retryFailedJob(4L)).isInstanceOf(InvestmentException.class);

            service.resolveQualityIssue(11L, 77L);
            Map<String, Object> resolved = jdbc.queryForMap("""
                    select resolution_status, resolved_at, updated_by, version
                    from investment_data_quality_issue where id = 11
                    """);
            assertThat(resolved).containsEntry("RESOLUTION_STATUS", "RESOLVED")
                    .containsEntry("UPDATED_BY", 77L).containsEntry("VERSION", 1L);
            assertThat(resolved.get("RESOLVED_AT")).isNotNull();
            assertThatThrownBy(() -> service.resolveQualityIssue(11L, 77L))
                    .isInstanceOf(InvestmentException.class);
            assertThatThrownBy(() -> service.resolveQualityIssue(12L, 0L))
                    .isInstanceOf(InvestmentException.class)
                    .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                            .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        } finally {
            database.shutdown();
        }
    }

    @Test
    void platformJobQueryRejectsPrivateDomainJobTypesBeforeTouchingStorage() {
        NamedParameterJdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        InvestmentMarketSubscriptionRegistry registry =
                org.mockito.Mockito.mock(InvestmentMarketSubscriptionRegistry.class);
        InvestmentPlatformQueryService service = new InvestmentPlatformQueryService(
                jdbcTemplate, registry, Clock.systemUTC());

        assertThatThrownBy(() -> service.jobs(null, "BACKTEST_RUN", 1, 20))
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.INVALID_REQUEST));
        verifyNoInteractions(jdbcTemplate);
    }

    private void assertForbidden(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(InvestmentException.class)
                .satisfies(exception -> assertThat(((InvestmentException) exception).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.FORBIDDEN));
    }

    private EmbeddedDatabase platformDatabase() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("""
                create table investment_job (
                    id bigint primary key, workspace_id bigint, job_type varchar(64), status varchar(32),
                    priority integer, attempts integer, max_attempts integer,
                    available_at timestamp with time zone, locked_at timestamp with time zone,
                    locked_by varchar(128), claim_token varchar(64), lease_expires_at timestamp with time zone,
                    heartbeat_at timestamp with time zone, last_error_code varchar(64),
                    last_error_message varchar(255), started_at timestamp with time zone,
                    finished_at timestamp with time zone, created_at timestamp with time zone,
                    updated_at timestamp with time zone)
                """);
        jdbc.execute("""
                create table investment_data_quality_issue (
                    id bigint primary key, instrument_id bigint, data_type varchar(64), price_type varchar(32),
                    interval_code varchar(32), point_time timestamp with time zone, issue_code varchar(64),
                    severity varchar(32), resolution_status varchar(32), resolved_at timestamp with time zone,
                    created_at timestamp with time zone, updated_at timestamp with time zone,
                    updated_by bigint, version bigint, deleted boolean)
                """);
        return database;
    }

    private InvestmentPlatformQueryService platformService(EmbeddedDatabase database) {
        return new InvestmentPlatformQueryService(new NamedParameterJdbcTemplate(database),
                org.mockito.Mockito.mock(InvestmentMarketSubscriptionRegistry.class),
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private void seedPlatformRows(JdbcTemplate jdbc) {
        Instant now = Instant.parse("2029-12-31T23:00:00Z");
        Object timestamp = now.atOffset(ZoneOffset.UTC);
        jdbc.update("""
                insert into investment_job (id, workspace_id, job_type, status, priority, attempts, max_attempts,
                    available_at, last_error_code, last_error_message, created_at, updated_at)
                values (?, ?, ?, ?, 100, ?, 3, ?, ?, ?, ?, ?)
                """, 1L, null, "BAR_INCREMENTAL", "FAILED", 3, timestamp, "PROVIDER", "failed", timestamp, timestamp);
        jdbc.update("""
                insert into investment_job (id, workspace_id, job_type, status, priority, attempts, max_attempts,
                    available_at, created_at, updated_at) values (?, ?, ?, ?, 100, 0, 3, ?, ?, ?)
                """, 2L, null, "QUOTE_REFRESH", "PENDING", timestamp,
                now.plusSeconds(1).atOffset(ZoneOffset.UTC), timestamp);
        jdbc.update("""
                insert into investment_job (id, workspace_id, job_type, status, priority, attempts, max_attempts,
                    available_at, created_at, updated_at) values (?, ?, ?, ?, 100, 1, 3, ?, ?, ?)
                """, 3L, null, "BACKTEST_RUN", "FAILED", timestamp,
                now.plusSeconds(2).atOffset(ZoneOffset.UTC), timestamp);
        jdbc.update("""
                insert into investment_job (id, workspace_id, job_type, status, priority, attempts, max_attempts,
                    available_at, created_at, updated_at) values (?, ?, ?, ?, 100, 1, 3, ?, ?, ?)
                """, 4L, 99L, "BAR_INCREMENTAL", "FAILED", timestamp,
                now.plusSeconds(3).atOffset(ZoneOffset.UTC), timestamp);
        jdbc.update("""
                insert into investment_data_quality_issue (id, instrument_id, data_type, price_type, interval_code,
                    point_time, issue_code, severity, resolution_status, resolved_at, created_at, updated_at,
                    version, deleted) values (?, 10, 'BAR', 'MARKET', 'M1', ?, 'GAP', ?, ?, ?, ?, ?, 0, false)
                """, 11L, timestamp, "ERROR", "OPEN", null, timestamp, timestamp);
        jdbc.update("""
                insert into investment_data_quality_issue (id, instrument_id, data_type, price_type, interval_code,
                    point_time, issue_code, severity, resolution_status, resolved_at, created_at, updated_at,
                    version, deleted) values (?, 10, 'BAR', 'MARKET', 'M1', ?, 'LATE', ?, ?, ?, ?, ?, 0, false)
                """, 12L, timestamp, "WARNING", "RESOLVED", timestamp,
                now.plusSeconds(1).atOffset(ZoneOffset.UTC), timestamp);
    }
}
