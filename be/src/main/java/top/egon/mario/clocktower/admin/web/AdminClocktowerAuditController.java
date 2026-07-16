package top.egon.mario.clocktower.admin.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditFilterRequest;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditReportResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerAuditSummaryResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerGameAuditResponse;
import top.egon.mario.clocktower.admin.dto.ClocktowerRoomAuditResponse;
import top.egon.mario.clocktower.admin.service.ClocktowerManagementAuditService;
import top.egon.mario.clocktower.chat.dto.ClocktowerChatMessageResponse;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/clocktower")
@Validated
public class AdminClocktowerAuditController extends ClocktowerReactiveSupport {

    private final ClocktowerManagementAuditService auditService;

    @GetMapping("/audit/summary")
    public Mono<ApiResponse<ClocktowerAuditSummaryResponse>> summary(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.summary(filter.toQuery(), principal));
    }

    @GetMapping("/audit/rooms")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Room>>> rooms(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.rooms(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/games")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Game>>> games(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.games(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/events")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Event>>> events(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.events(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/conversations")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Conversation>>> conversations(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.conversations(
                filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/messages")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Message>>> messages(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.messages(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/members")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Member>>> members(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.members(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/invitations")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Invitation>>> invitations(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.invitations(
                filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/audit/bans")
    public Mono<ApiResponse<PageResult<ClocktowerAuditReportResponse.Ban>>> bans(
            @Valid @ModelAttribute ClocktowerAuditFilterRequest filter,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> pageResult(auditService.bans(filter.toQuery(), pageable(page, size), principal)));
    }

    @GetMapping("/rooms/{roomId}/audit")
    public Mono<ApiResponse<ClocktowerRoomAuditResponse>> auditRoom(@PathVariable Long roomId,
                                                                    @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.auditRoom(roomId, principal));
    }

    @GetMapping("/games/{gameId}/audit")
    public Mono<ApiResponse<ClocktowerGameAuditResponse>> auditGame(@PathVariable Long gameId,
                                                                    @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> auditService.auditGame(gameId, principal));
    }

    @GetMapping("/chat/conversations/{conversationId}/messages")
    public Mono<ApiResponse<PageResult<ClocktowerChatMessageResponse>>> messages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @AuthenticationPrincipal RbacPrincipal principal) {
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.min(size, 200));
        return blocking(() -> pageResult(auditService.messages(conversationId, pageRequest, principal)));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page - 1, 0), Math.min(size, 200));
    }

    private <T> PageResult<T> pageResult(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
