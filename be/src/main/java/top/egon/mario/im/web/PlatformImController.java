package top.egon.mario.im.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.im.platform.PlatformInvitationFacade;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.im.platform.PlatformRoomFacade;
import top.egon.mario.im.platform.dto.PlatformBootstrapView;
import top.egon.mario.im.platform.dto.PlatformConversationView;
import top.egon.mario.im.platform.dto.PlatformInvitationView;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/im/platform")
public class PlatformImController extends ReactiveImSupport {

    private final PlatformImFacade platformImFacade;
    private final PlatformRoomFacade platformRoomFacade;
    private final PlatformInvitationFacade platformInvitationFacade;

    public PlatformImController(PlatformImFacade platformImFacade,
                                PlatformRoomFacade platformRoomFacade,
                                PlatformInvitationFacade platformInvitationFacade) {
        this.platformImFacade = platformImFacade;
        this.platformRoomFacade = platformRoomFacade;
        this.platformInvitationFacade = platformInvitationFacade;
    }

    @GetMapping("/bootstrap")
    public Mono<ApiResponse<PlatformBootstrapView>> bootstrap(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformImFacade.bootstrap(imPrincipal(principal)));
    }

    @GetMapping("/conversations")
    public Mono<ApiResponse<List<PlatformConversationView>>> listConversations(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformImFacade.listConversations(imPrincipal(principal)));
    }

    @PostMapping("/channels")
    public Mono<ApiResponse<ChannelView>> createChannel(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestBody SurfaceCreateRequest request) {
        return blocking(() -> platformRoomFacade.createChannel(
                imPrincipal(principal),
                request == null ? null : request.name(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/channels")
    public Mono<ApiResponse<List<ChannelView>>> listChannels(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformRoomFacade.listChannels(imPrincipal(principal)));
    }

    @PostMapping("/groups")
    public Mono<ApiResponse<GroupView>> createStandaloneGroup(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestBody SurfaceCreateRequest request) {
        return blocking(() -> platformRoomFacade.createStandaloneGroup(
                imPrincipal(principal),
                request == null ? null : request.name(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/groups")
    public Mono<ApiResponse<List<GroupView>>> listStandaloneGroups(
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> platformRoomFacade.listGroups(imPrincipal(principal)));
    }

    @PostMapping("/channels/{channelId}/groups")
    public Mono<ApiResponse<GroupView>> createChannelGroup(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long channelId,
            @RequestBody ChannelGroupCreateRequest request) {
        return blocking(() -> platformRoomFacade.createChannelGroup(
                imPrincipal(principal),
                channelId,
                request == null ? null : request.name(),
                request == null ? null : request.joinPolicy(),
                request == null ? null : request.metadataJson()));
    }

    @GetMapping("/channels/{channelId}/groups")
    public Mono<ApiResponse<List<GroupView>>> listChannelGroups(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long channelId) {
        return blocking(() -> platformRoomFacade.listChannelGroups(imPrincipal(principal), channelId));
    }

    @PostMapping("/surfaces/{surfaceType}/{surfaceId}/invitations")
    public Mono<ApiResponse<PlatformInvitationView>> invite(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable String surfaceType,
            @PathVariable Long surfaceId,
            @RequestBody InvitationRequest request) {
        return blocking(() -> platformInvitationFacade.invite(
                imPrincipal(principal),
                surfaceType,
                surfaceId,
                request == null ? null : request.inviteeUserId(),
                request == null ? null : request.message()));
    }

    @GetMapping("/invitations")
    public Mono<ApiResponse<PageResult<PlatformInvitationView>>> listInvitations(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(platformInvitationFacade.listIncoming(
                imPrincipal(principal), Math.max(page - 1, 0), size)));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public Mono<ApiResponse<PlatformInvitationView>> acceptInvitation(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long invitationId) {
        return blocking(() -> platformInvitationFacade.accept(imPrincipal(principal), invitationId));
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public Mono<ApiResponse<PlatformInvitationView>> rejectInvitation(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long invitationId) {
        return blocking(() -> platformInvitationFacade.reject(imPrincipal(principal), invitationId));
    }

    @PostMapping("/surfaces/{surfaceType}/{surfaceId}/owner")
    public Mono<ApiResponse<Void>> transferOwnership(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable String surfaceType,
            @PathVariable Long surfaceId,
            @RequestBody OwnershipTransferRequest request) {
        return blockingVoid(() -> platformInvitationFacade.transferOwnership(
                imPrincipal(principal),
                surfaceType,
                surfaceId,
                request == null ? null : request.newOwnerUserId()));
    }

    public record SurfaceCreateRequest(String name, String metadataJson) {
    }

    public record ChannelGroupCreateRequest(String name, String joinPolicy, String metadataJson) {
    }

    public record InvitationRequest(Long inviteeUserId, String message) {
    }

    public record OwnershipTransferRequest(Long newOwnerUserId) {
    }
}
