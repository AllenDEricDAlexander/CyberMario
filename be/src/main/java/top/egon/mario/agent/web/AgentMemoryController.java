package top.egon.mario.agent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.agent.memory.dto.request.AgentMemorySessionQuery;
import top.egon.mario.agent.memory.dto.request.AgentMemorySessionRequest;
import top.egon.mario.agent.memory.dto.response.AgentLongTermMemoryResponse;
import top.egon.mario.agent.memory.dto.response.AgentLongTermMemoryVersionResponse;
import top.egon.mario.agent.memory.dto.response.AgentMemoryExtractionAuditResponse;
import top.egon.mario.agent.memory.dto.response.AgentMemoryMessageResponse;
import top.egon.mario.agent.memory.dto.response.AgentMemorySessionResponse;
import top.egon.mario.agent.memory.service.AgentLongTermMemoryService;
import top.egon.mario.agent.memory.service.AgentMemoryExtractionService;
import top.egon.mario.agent.memory.service.AgentMemoryMessageService;
import top.egon.mario.agent.memory.service.AgentMemorySessionService;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionCreate;
import top.egon.mario.agent.memory.service.model.AgentMemorySessionUpdate;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Current-user Agent memory management endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/memory")
@Validated
public class AgentMemoryController extends ReactiveAgentSupport {

    private final AgentMemorySessionService sessionService;
    private final AgentMemoryMessageService messageService;
    private final AgentLongTermMemoryService longTermMemoryService;
    private final AgentMemoryExtractionService extractionService;

    @GetMapping("/sessions")
    public Mono<ApiResponse<PageResult<AgentMemorySessionResponse>>> sessions(
            @ModelAttribute AgentMemorySessionQuery query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(sessionService.page(
                query == null ? null : query.entryType(),
                query == null ? null : query.status(),
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by("updatedAt").descending()),
                principal).map(AgentMemorySessionResponse::from)));
    }

    @PostMapping("/sessions")
    public Mono<ApiResponse<AgentMemorySessionResponse>> create(@Valid @RequestBody AgentMemorySessionRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentMemorySessionResponse.from(sessionService.create(
                new AgentMemorySessionCreate(request.entryType(), request.title(), request.memoryEnabled(),
                        request.longTermExtractionEnabled()), principal)));
    }

    @PatchMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<AgentMemorySessionResponse>> update(@PathVariable String sessionId,
                                                                @Valid @RequestBody AgentMemorySessionRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentMemorySessionResponse.from(sessionService.update(sessionId,
                new AgentMemorySessionUpdate(request.title(), request.memoryEnabled(),
                        request.longTermExtractionEnabled()), principal)));
    }

    @PostMapping("/sessions/{sessionId}/release")
    public Mono<ApiResponse<AgentMemorySessionResponse>> release(@PathVariable String sessionId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentMemorySessionResponse.from(sessionService.release(sessionId, principal)));
    }

    @PostMapping("/sessions/{sessionId}/restore")
    public Mono<ApiResponse<AgentMemorySessionResponse>> restore(@PathVariable String sessionId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentMemorySessionResponse.from(sessionService.restore(sessionId, principal)));
    }

    @PostMapping("/sessions/{sessionId}/archive")
    public Mono<ApiResponse<AgentMemorySessionResponse>> archive(@PathVariable String sessionId,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentMemorySessionResponse.from(sessionService.archive(sessionId, principal)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Void>> deleteArchived(@PathVariable String sessionId,
                                                  @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> sessionService.deleteArchived(sessionId, principal));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Mono<ApiResponse<List<AgentMemoryMessageResponse>>> messages(@PathVariable String sessionId,
                                                                        @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> messageService.messages(sessionId, principal).stream()
                .map(AgentMemoryMessageResponse::from)
                .toList());
    }

    @GetMapping("/long-term")
    public Mono<ApiResponse<AgentLongTermMemoryResponse>> longTerm(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> AgentLongTermMemoryResponse.from(
                longTermMemoryService.getOrCreateUserAgentMemory(principal)));
    }

    @GetMapping("/long-term/versions")
    public Mono<ApiResponse<List<AgentLongTermMemoryVersionResponse>>> versions(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> longTermMemoryService.userAgentVersions(principal).stream()
                .map(AgentLongTermMemoryVersionResponse::from)
                .toList());
    }

    @GetMapping("/extractions")
    public Mono<ApiResponse<List<AgentMemoryExtractionAuditResponse>>> extractions(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> extractionService.userAudits(principal).stream()
                .map(AgentMemoryExtractionAuditResponse::from)
                .toList());
    }
}
