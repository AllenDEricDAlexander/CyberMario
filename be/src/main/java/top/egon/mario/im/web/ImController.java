package top.egon.mario.im.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.im.facade.DmFacade;
import top.egon.mario.im.facade.GovFacade;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.AnnounceCommand;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.CancelJoinCommand;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.LeaveCommand;
import top.egon.mario.im.facade.dto.command.MarkReadCommand;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.command.RejectJoinCommand;
import top.egon.mario.im.facade.dto.command.RemoveMemberCommand;
import top.egon.mario.im.facade.dto.command.SendMessageCommand;
import top.egon.mario.im.facade.dto.query.HistoryQuery;
import top.egon.mario.im.facade.dto.query.ListJoinRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListConversationsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.query.ListSurfaceMembersQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.facade.dto.view.JoinRequestView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.UnreadView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.facade.dto.view.SurfaceMemberView;
import top.egon.mario.im.platform.PlatformImFacade;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/im")
public class ImController extends ReactiveImSupport {

    private final ImFacade imFacade;
    private final PlatformImFacade platformImFacade;
    private final RoomFacade roomFacade;
    private final DmFacade dmFacade;
    private final GovFacade govFacade;

    @GetMapping("/conversations")
    public Mono<ApiResponse<List<ConversationView>>> listConversations(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestParam(required = false) String contextType,
            @RequestParam(required = false) Long contextId) {
        return blocking(() -> imFacade.listConversations(new ListConversationsQuery(
                imPrincipal(principal), contextType, contextId)));
    }

    @PostMapping("/messages")
    public Mono<ApiResponse<MessageView>> sendMessage(@AuthenticationPrincipal RbacPrincipal principal,
                                                      @RequestBody SendMessageRequest request) {
        return blocking(() -> platformImFacade.send(new SendMessageCommand(
                imPrincipal(principal),
                request == null ? null : request.conversationId(),
                request == null ? null : request.clientMsgId(),
                request == null ? null : request.messageType(),
                request == null ? null : request.content(),
                request == null ? null : request.payloadJson(),
                request == null ? null : request.metadataJson()
        )));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Mono<ApiResponse<PageResult<MessageView>>> history(@AuthenticationPrincipal RbacPrincipal principal,
                                                              @PathVariable Long conversationId,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(required = false) Long beforeSeq,
                                                              @RequestParam(required = false) Long afterSeq) {
        return blocking(() -> pageResult(imFacade.history(new HistoryQuery(
                imPrincipal(principal), conversationId, Math.max(page - 1, 0), size, beforeSeq, afterSeq))));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public Mono<ApiResponse<UnreadView>> markRead(@AuthenticationPrincipal RbacPrincipal principal,
                                                  @PathVariable Long conversationId,
                                                  @RequestBody(required = false) MarkReadRequest request) {
        return blocking(() -> imFacade.markRead(new MarkReadCommand(
                imPrincipal(principal), conversationId, request == null ? null : request.messageSeq())));
    }

    @PostMapping("/channels")
    public Mono<ApiResponse<ChannelView>> createChannel(@AuthenticationPrincipal RbacPrincipal principal,
                                                        @RequestBody CreateChannelRequest request) {
        return blocking(() -> roomFacade.createChannel(new CreateChannelCommand(
                imPrincipal(principal),
                request == null ? null : request.contextType(),
                request == null ? null : request.contextId(),
                request == null ? null : request.channelKey(),
                request == null ? null : request.name(),
                request == null ? null : request.joinPolicy(),
                request == null ? null : request.metadataJson()
        )));
    }

    @GetMapping("/channels")
    public Mono<ApiResponse<List<ChannelView>>> listChannels(@AuthenticationPrincipal RbacPrincipal principal,
                                                             @RequestParam String contextType,
                                                             @RequestParam(required = false) Long contextId) {
        return blocking(() -> roomFacade.listChannels(new ListChannelsQuery(
                imPrincipal(principal), contextType, contextId)));
    }

    @PostMapping("/groups")
    public Mono<ApiResponse<GroupView>> createGroup(@AuthenticationPrincipal RbacPrincipal principal,
                                                    @RequestBody CreateGroupRequest request) {
        return blocking(() -> roomFacade.createGroup(new CreateGroupCommand(
                imPrincipal(principal),
                request == null ? null : request.channelId(),
                request == null ? null : request.contextType(),
                request == null ? null : request.contextId(),
                request == null ? null : request.groupKey(),
                request == null ? null : request.name(),
                request == null ? null : request.joinPolicy(),
                request == null ? null : request.metadataJson()
        )));
    }

    @GetMapping("/groups")
    public Mono<ApiResponse<List<GroupView>>> listGroups(@AuthenticationPrincipal RbacPrincipal principal,
                                                         @RequestParam(required = false) Long channelId,
                                                         @RequestParam(required = false) String contextType,
                                                         @RequestParam(required = false) Long contextId) {
        return blocking(() -> roomFacade.listGroups(new ListGroupsQuery(
                imPrincipal(principal), channelId, contextType, contextId)));
    }

    @PostMapping("/join-requests")
    public Mono<ApiResponse<JoinResultView>> applyJoin(@AuthenticationPrincipal RbacPrincipal principal,
                                                       @RequestBody JoinRequest request) {
        return blocking(() -> roomFacade.applyJoin(new JoinCommand(
                imPrincipal(principal),
                request == null ? null : request.surfaceType(),
                request == null ? null : request.surfaceId(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/join-requests/{id}/approve")
    public Mono<ApiResponse<JoinResultView>> approveJoin(@AuthenticationPrincipal RbacPrincipal principal,
                                                         @PathVariable Long id) {
        return blocking(() -> roomFacade.approveJoin(new ApproveCommand(imPrincipal(principal), id)));
    }

    @PostMapping("/join-requests/{id}/reject")
    public Mono<ApiResponse<JoinResultView>> rejectJoin(@AuthenticationPrincipal RbacPrincipal principal,
                                                        @PathVariable Long id,
                                                        @RequestBody(required = false) RejectJoinRequest request) {
        return blocking(() -> roomFacade.rejectJoin(new RejectJoinCommand(
                imPrincipal(principal), id, request == null ? null : request.reason())));
    }

    @PostMapping("/join-requests/{id}/cancel")
    public Mono<ApiResponse<JoinResultView>> cancelJoin(@AuthenticationPrincipal RbacPrincipal principal,
                                                        @PathVariable Long id) {
        return blocking(() -> roomFacade.cancelJoin(new CancelJoinCommand(imPrincipal(principal), id)));
    }

    @PostMapping("/surfaces/{surfaceType}/{surfaceId}/leave")
    public Mono<ApiResponse<Void>> leave(@AuthenticationPrincipal RbacPrincipal principal,
                                         @PathVariable String surfaceType,
                                         @PathVariable Long surfaceId) {
        return blockingVoid(() -> roomFacade.leave(new LeaveCommand(imPrincipal(principal), surfaceType, surfaceId)));
    }

    @GetMapping("/surfaces/{surfaceType}/{surfaceId}/members")
    public Mono<ApiResponse<PageResult<SurfaceMemberView>>> listSurfaceMembers(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable String surfaceType,
            @PathVariable Long surfaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return blocking(() -> pageResult(roomFacade.listMembers(new ListSurfaceMembersQuery(
                imPrincipal(principal), surfaceType, surfaceId, Math.max(page - 1, 0), size))));
    }

    @GetMapping("/surfaces/{surfaceType}/{surfaceId}/join-requests")
    public Mono<ApiResponse<PageResult<JoinRequestView>>> listSurfaceJoinRequests(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable String surfaceType,
            @PathVariable Long surfaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return blocking(() -> pageResult(roomFacade.listJoinRequests(new ListJoinRequestsQuery(
                imPrincipal(principal), surfaceType, surfaceId, Math.max(page - 1, 0), size))));
    }

    @DeleteMapping("/surfaces/{surfaceType}/{surfaceId}/members/{userId}")
    public Mono<ApiResponse<Void>> removeSurfaceMember(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable String surfaceType,
            @PathVariable Long surfaceId,
            @PathVariable Long userId) {
        return blockingVoid(() -> roomFacade.removeMember(new RemoveMemberCommand(
                imPrincipal(principal), surfaceType, surfaceId, userId)));
    }

    @PostMapping("/dms")
    public Mono<ApiResponse<ConversationView>> openDm(@AuthenticationPrincipal RbacPrincipal principal,
                                                      @RequestBody OpenDmRequest request) {
        return blocking(() -> platformImFacade.openDm(new OpenDmCommand(
                imPrincipal(principal), request == null ? null : request.targetUserId())));
    }

    @PostMapping("/dms/block")
    public Mono<ApiResponse<Void>> block(@AuthenticationPrincipal RbacPrincipal principal,
                                         @RequestBody BlockUserRequest request) {
        return blockingVoid(() -> dmFacade.block(new BlockUserCommand(
                imPrincipal(principal),
                request == null ? null : request.targetUserId(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/dms/unblock")
    public Mono<ApiResponse<Void>> unblock(@AuthenticationPrincipal RbacPrincipal principal,
                                           @RequestBody BlockUserRequest request) {
        return blockingVoid(() -> dmFacade.unblock(new BlockUserCommand(
                imPrincipal(principal),
                request == null ? null : request.targetUserId(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/governance/mute")
    public Mono<ApiResponse<Void>> mute(@AuthenticationPrincipal RbacPrincipal principal,
                                        @RequestBody MuteUserRequest request) {
        return blockingVoid(() -> govFacade.mute(new MuteUserCommand(
                imPrincipal(principal),
                request == null ? null : request.surfaceType(),
                request == null ? null : request.surfaceId(),
                request == null ? null : request.userId(),
                request == null ? null : request.mutedUntil(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/governance/global-mute")
    public Mono<ApiResponse<Void>> globalMute(@AuthenticationPrincipal RbacPrincipal principal,
                                              @RequestBody GlobalMuteRequest request) {
        return blockingVoid(() -> govFacade.globalMute(new GlobalMuteCommand(
                imPrincipal(principal),
                request == null ? null : request.scopeType(),
                request == null ? null : request.scopeId(),
                request == null ? null : request.userId(),
                request == null ? null : request.mutedUntil(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/governance/announcement")
    public Mono<ApiResponse<Void>> announce(@AuthenticationPrincipal RbacPrincipal principal,
                                            @RequestBody AnnouncementRequest request) {
        return blockingVoid(() -> govFacade.announce(new AnnounceCommand(
                imPrincipal(principal),
                request == null ? null : request.surfaceType(),
                request == null ? null : request.surfaceId(),
                request == null ? null : request.announcement()
        )));
    }

    @PostMapping("/governance/ban")
    public Mono<ApiResponse<Void>> ban(@AuthenticationPrincipal RbacPrincipal principal,
                                       @RequestBody BanUserRequest request) {
        return blockingVoid(() -> govFacade.ban(new BanUserCommand(
                imPrincipal(principal),
                request == null ? null : request.surfaceType(),
                request == null ? null : request.surfaceId(),
                request == null ? null : request.userId(),
                request == null ? null : request.reason()
        )));
    }

    @PostMapping("/ws-ticket")
    public Mono<ApiResponse<WsTicketView>> mintWsTicket(@AuthenticationPrincipal RbacPrincipal principal,
                                                        @RequestBody(required = false) MintWsTicketRequest request) {
        return blocking(() -> imFacade.mintWsTicket(new MintWsTicketCommand(
                imPrincipal(principal),
                request == null ? null : request.conversationId()
        )));
    }

    public record SendMessageRequest(
            Long conversationId,
            String clientMsgId,
            String messageType,
            String content,
            String payloadJson,
            String metadataJson) {
    }

    public record MarkReadRequest(Long messageSeq) {
    }

    public record CreateChannelRequest(
            String contextType,
            Long contextId,
            String channelKey,
            String name,
            String joinPolicy,
            String metadataJson) {
    }

    public record CreateGroupRequest(
            Long channelId,
            String contextType,
            Long contextId,
            String groupKey,
            String name,
            String joinPolicy,
            String metadataJson) {
    }

    public record JoinRequest(String surfaceType, Long surfaceId, String reason) {
    }

    public record RejectJoinRequest(String reason) {
    }

    public record OpenDmRequest(Long targetUserId) {
    }

    public record BlockUserRequest(Long targetUserId, String reason) {
    }

    public record MuteUserRequest(
            String surfaceType,
            Long surfaceId,
            Long userId,
            Instant mutedUntil,
            String reason) {
    }

    public record GlobalMuteRequest(
            String scopeType,
            Long scopeId,
            Long userId,
            Instant mutedUntil,
            String reason) {
    }

    public record AnnouncementRequest(String surfaceType, Long surfaceId, String announcement) {
    }

    public record BanUserRequest(String surfaceType, Long surfaceId, Long userId, String reason) {
    }

    public record MintWsTicketRequest(Long conversationId) {
    }
}
