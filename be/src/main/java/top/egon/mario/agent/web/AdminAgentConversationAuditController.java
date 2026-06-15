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
import top.egon.mario.agent.dto.request.AgentConversationAuditQuery;
import top.egon.mario.agent.dto.response.AgentConversationAuditResponse;
import top.egon.mario.agent.dto.response.AgentConversationMessageAuditResponse;
import top.egon.mario.agent.po.enums.AgentConversationStatus;
import top.egon.mario.agent.service.AgentConversationAuditService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

/**
 * Super-admin endpoints for original Agent conversation audit history.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/agent/conversation-audits")
@Validated
public class AdminAgentConversationAuditController extends ReactiveAgentSupport {

    private final AgentConversationAuditService auditService;

    @GetMapping
    public Mono<ApiResponse<PageResult<AgentConversationAuditResponse>>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) Long presetId,
            @RequestParam(required = false) AgentConversationStatus status,
            @AuthenticationPrincipal RbacPrincipal principal) {
        AgentConversationAuditQuery query = new AgentConversationAuditQuery(startAt, endAt, userId, username,
                threadId, presetId, status);
        return blocking(() -> pageResult(auditService.page(query,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("id").descending()), principal)));
    }

    @GetMapping("/{id}/messages")
    public Mono<ApiResponse<List<AgentConversationMessageAuditResponse>>> messages(@PathVariable @Min(1) Long id,
                                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.messages(id, principal));
    }

}
