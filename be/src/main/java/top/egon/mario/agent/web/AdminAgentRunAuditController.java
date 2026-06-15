package top.egon.mario.agent.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.observability.dto.request.AgentRunAuditQuery;
import top.egon.mario.agent.observability.dto.response.AgentRunAuditResponse;
import top.egon.mario.agent.observability.dto.response.AgentRunEventAuditResponse;
import top.egon.mario.agent.observability.po.enums.AgentRunAuditStatus;
import top.egon.mario.agent.observability.service.AgentRunAuditService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Super-admin endpoints for unified Agent run audit timelines.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/run-audits")
@Validated
public class AdminAgentRunAuditController extends ReactiveAgentSupport {

    private final AgentRunAuditService auditService;

    @GetMapping
    public Mono<ApiResponse<PageResult<AgentRunAuditResponse>>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Long presetId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String mcpServerCode,
            @RequestParam(required = false) AgentRunAuditStatus status,
            @AuthenticationPrincipal RbacPrincipal principal) {
        AgentRunAuditQuery query = new AgentRunAuditQuery(startAt, endAt, userId, username, threadId,
                requestId, traceId, presetId, toolName, mcpServerCode, status);
        return blocking(() -> pageResult(auditService.page(query,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()), principal)));
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<AgentRunAuditResponse>> detail(@PathVariable @Min(1) Long id,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.detail(id, principal));
    }

    @GetMapping("/{id}/events")
    public Mono<ApiResponse<List<AgentRunEventAuditResponse>>> events(@PathVariable @Min(1) Long id,
                                                                      @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.events(id, principal));
    }
}
