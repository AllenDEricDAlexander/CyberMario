package top.egon.mario.agent.model.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.enums.ModelAuditDashboardScope;
import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditDashboardSummaryResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditDimensionStatResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditOverviewResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditRecentCallResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditTrendPointResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.agent.model.dto.response.ModelAuditUserStatResponse;
import top.egon.mario.agent.model.po.ModelAuditPo;
import top.egon.mario.agent.model.po.enums.ModelAuditStatus;
import top.egon.mario.agent.model.repository.ModelAuditDashboardRepository;
import top.egon.mario.agent.model.service.ModelAuditDashboardService;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.service.RbacException;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates persisted model audit records for dashboard reporting.
 */
@Service
@RequiredArgsConstructor
public class ModelAuditDashboardServiceImpl implements ModelAuditDashboardService {

    private static final String SELF_PERMISSION = "api:agent:model-audit:dashboard:self";
    private static final String GLOBAL_PERMISSION = "api:agent:model-audit:dashboard:global";
    private static final Duration DEFAULT_RANGE = Duration.ofDays(7);
    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private static final int AUDIT_LIMIT = 100_000;
    private static final int STAT_LIMIT = 20;

    private final ModelAuditDashboardRepository dashboardRepository;

    @Override
    @Transactional(readOnly = true)
    public ModelAuditDashboardSummaryResponse selfSummary(ModelAuditDashboardQuery query, RbacPrincipal principal) {
        ModelAuditDashboardQuery normalized = normalizeSelfQuery(query, principal);
        return summary(ModelAuditDashboardScope.SELF, normalized, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ModelAuditDashboardSummaryResponse globalSummary(ModelAuditDashboardQuery query, RbacPrincipal principal) {
        ModelAuditDashboardQuery normalized = normalizeGlobalQuery(query, principal);
        return summary(ModelAuditDashboardScope.GLOBAL, normalized, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAuditRecentCallResponse> selfRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                              RbacPrincipal principal) {
        ModelAuditDashboardQuery normalized = normalizeSelfQuery(query, principal);
        return recentCalls(normalized, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelAuditRecentCallResponse> globalRecentCalls(ModelAuditDashboardQuery query, Pageable pageable,
                                                                RbacPrincipal principal) {
        ModelAuditDashboardQuery normalized = normalizeGlobalQuery(query, principal);
        return recentCalls(normalized, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelAuditUserOptionResponse> userOptions(String keyword, int size, RbacPrincipal principal) {
        requireGlobalPermission(principal);
        return dashboardRepository.userOptions(keyword, Math.min(Math.max(size, 1), 50));
    }

    private ModelAuditDashboardQuery normalizeSelfQuery(ModelAuditDashboardQuery query, RbacPrincipal principal) {
        requirePermission(principal, SELF_PERMISSION);
        if (query != null && query.userId() != null && !Objects.equals(query.userId(), principal.userId())) {
            throw forbidden();
        }
        return normalize(query, principal.userId());
    }

    private ModelAuditDashboardQuery normalizeGlobalQuery(ModelAuditDashboardQuery query, RbacPrincipal principal) {
        requireGlobalPermission(principal);
        return normalize(query, query == null ? null : query.userId());
    }

    private ModelAuditDashboardSummaryResponse summary(ModelAuditDashboardScope scope, ModelAuditDashboardQuery query, boolean includeUserStats) {
        List<ModelAuditPo> audits = dashboardRepository.findAudits(query, AUDIT_LIMIT);
        return new ModelAuditDashboardSummaryResponse(
                scope,
                query.startAt(),
                query.endAt(),
                overview(audits),
                tokenTrend(audits),
                callTrend(audits),
                dimensionStats(query, "provider"),
                dimensionStats(query, "model"),
                dimensionStats(query, "scenario"),
                dimensionStats(query, "status"),
                includeUserStats ? userStats(query) : List.of()
        );
    }

    private Page<ModelAuditRecentCallResponse> recentCalls(ModelAuditDashboardQuery query, Pageable pageable) {
        Page<ModelAuditPo> page = dashboardRepository.recentCalls(query, pageable);
        Map<Long, UserPo> usersById = usersById(page.getContent().stream()
                .map(ModelAuditPo::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return new PageImpl<>(recentCalls(page.getContent(), usersById), pageable, page.getTotalElements());
    }

    private ModelAuditDashboardQuery normalize(ModelAuditDashboardQuery query, Long userId) {
        Instant now = Instant.now();
        Instant endAt = query == null || query.endAt() == null ? now : query.endAt();
        Instant startAt = query == null || query.startAt() == null ? endAt.minus(DEFAULT_RANGE) : query.startAt();
        if (!startAt.isBefore(endAt)) {
            throw new RbacException("MODEL_AUDIT_DASHBOARD_RANGE_INVALID", "startAt must be before endAt");
        }
        if (Duration.between(startAt, endAt).compareTo(MAX_RANGE) > 0) {
            throw new RbacException("MODEL_AUDIT_DASHBOARD_RANGE_TOO_LARGE", "dashboard range cannot exceed 90 days");
        }
        return new ModelAuditDashboardQuery(
                startAt,
                endAt,
                userId,
                query == null ? null : query.provider(),
                query == null ? null : trimToNull(query.model()),
                query == null ? null : query.scenario(),
                query == null ? null : query.status()
        );
    }

    private ModelAuditOverviewResponse overview(List<ModelAuditPo> audits) {
        long callCount = audits.size();
        long successCount = audits.stream().filter(audit -> audit.getStatus() == ModelAuditStatus.SUCCESS).count();
        long failedCount = audits.stream().filter(audit -> audit.getStatus() == ModelAuditStatus.FAILED).count();
        long promptTokens = audits.stream().mapToLong(audit -> nullToZero(audit.getPromptTokens())).sum();
        long completionTokens = audits.stream().mapToLong(audit -> nullToZero(audit.getCompletionTokens())).sum();
        long totalTokens = audits.stream().mapToLong(audit -> nullToZero(audit.getTotalTokens())).sum();
        long promptChars = audits.stream().mapToLong(audit -> nullToZero(audit.getPromptChars())).sum();
        long completionChars = audits.stream().mapToLong(audit -> nullToZero(audit.getCompletionChars())).sum();
        double avgDurationMs = audits.stream()
                .map(ModelAuditPo::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0D);
        long streamingCount = audits.stream().filter(ModelAuditPo::isStreaming).count();
        double successRate = callCount == 0 ? 0D : (double) successCount / callCount;
        return new ModelAuditOverviewResponse(callCount, successCount, failedCount, successRate,
                promptTokens, completionTokens, totalTokens, promptChars, completionChars, avgDurationMs, streamingCount);
    }

    private List<ModelAuditTrendPointResponse> tokenTrend(List<ModelAuditPo> audits) {
        Map<LocalDate, long[]> valuesByDate = new LinkedHashMap<>();
        audits.stream()
                .sorted(Comparator.comparing(ModelAuditPo::getCreatedAt))
                .forEach(audit -> {
                    LocalDate date = audit.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                    long[] values = valuesByDate.computeIfAbsent(date, ignored -> new long[3]);
                    values[0] += nullToZero(audit.getPromptTokens());
                    values[1] += nullToZero(audit.getCompletionTokens());
                    values[2] += nullToZero(audit.getTotalTokens());
                });
        return valuesByDate.entrySet().stream()
                .flatMap(entry -> List.of(
                        new ModelAuditTrendPointResponse(entry.getKey(), "promptTokens", entry.getValue()[0]),
                        new ModelAuditTrendPointResponse(entry.getKey(), "completionTokens", entry.getValue()[1]),
                        new ModelAuditTrendPointResponse(entry.getKey(), "totalTokens", entry.getValue()[2])
                ).stream())
                .toList();
    }

    private List<ModelAuditTrendPointResponse> callTrend(List<ModelAuditPo> audits) {
        return audits.stream()
                .collect(Collectors.groupingBy(
                        audit -> audit.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ModelAuditTrendPointResponse(entry.getKey(), "callCount", entry.getValue()))
                .toList();
    }

    private List<ModelAuditDimensionStatResponse> dimensionStats(ModelAuditDashboardQuery query, String fieldName) {
        return dashboardRepository.dimensionStats(query, fieldName, STAT_LIMIT).stream()
                .map(row -> new ModelAuditDimensionStatResponse(row.name(), row.callCount(), row.totalTokens(), row.avgDurationMs()))
                .toList();
    }

    private List<ModelAuditUserStatResponse> userStats(ModelAuditDashboardQuery query) {
        List<ModelAuditDashboardRepository.UserStatRow> rows = dashboardRepository.userStats(query, STAT_LIMIT);
        Map<Long, UserPo> usersById = usersById(rows.stream()
                .map(ModelAuditDashboardRepository.UserStatRow::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return rows.stream()
                .map(row -> {
                    UserPo user = usersById.get(row.userId());
                    return new ModelAuditUserStatResponse(row.userId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getNickname(),
                            row.callCount(),
                            row.totalTokens());
                })
                .toList();
    }

    private List<ModelAuditRecentCallResponse> recentCalls(List<ModelAuditPo> audits, Map<Long, UserPo> usersById) {
        return audits.stream()
                .map(audit -> {
                    UserPo user = usersById.get(audit.getUserId());
                    return new ModelAuditRecentCallResponse(
                            audit.getId(),
                            audit.getCreatedAt(),
                            audit.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getNickname(),
                            audit.getProvider(),
                            audit.getModel(),
                            audit.getScenario(),
                            audit.getStatus(),
                            audit.getPromptTokens(),
                            audit.getCompletionTokens(),
                            audit.getTotalTokens(),
                            audit.getDurationMs(),
                            audit.getTraceId()
                    );
                })
                .toList();
    }

    private Map<Long, UserPo> usersById(Collection<Long> userIds) {
        return dashboardRepository.users(userIds).stream()
                .collect(Collectors.toMap(UserPo::getId, Function.identity()));
    }

    private void requirePermission(RbacPrincipal principal, String permissionCode) {
        if (principal == null || !principal.apiAuthorities().contains(permissionCode)) {
            throw forbidden();
        }
    }

    private void requireGlobalPermission(RbacPrincipal principal) {
        requirePermission(principal, GLOBAL_PERMISSION);
    }

    private RbacException forbidden() {
        return new RbacException("MODEL_AUDIT_DASHBOARD_FORBIDDEN", "model audit dashboard access denied");
    }

    private long nullToZero(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
