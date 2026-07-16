package top.egon.mario.im.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.common.api.PageResult;
import top.egon.mario.im.facade.FriendFacade;
import top.egon.mario.im.facade.dto.command.CancelFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.DecideFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.UpdateFriendRemarkCommand;
import top.egon.mario.im.facade.dto.query.ListFriendRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListFriendsQuery;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.FriendView;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequestMapping("/api/im/platform")
public class PlatformFriendController extends ReactiveImSupport {

    private final FriendFacade friendFacade;

    public PlatformFriendController(FriendFacade friendFacade) {
        this.friendFacade = friendFacade;
    }

    @GetMapping("/users")
    public Mono<ApiResponse<PageResult<UserDirectoryItemResponse>>> searchUsers(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(friendFacade.searchUsers(
                imPrincipal(principal), keyword, Math.max(page - 1, 0), size)));
    }

    @GetMapping("/friends")
    public Mono<ApiResponse<PageResult<FriendView>>> listFriends(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(friendFacade.listFriends(new ListFriendsQuery(
                imPrincipal(principal), Math.max(page - 1, 0), size))));
    }

    @GetMapping("/friend-requests")
    public Mono<ApiResponse<PageResult<FriendRequestView>>> listFriendRequests(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestParam(defaultValue = "INCOMING") String box,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocking(() -> pageResult(friendFacade.listRequests(new ListFriendRequestsQuery(
                imPrincipal(principal), box, Math.max(page - 1, 0), size))));
    }

    @PostMapping("/friend-requests")
    public Mono<ApiResponse<FriendRequestView>> requestFriend(
            @AuthenticationPrincipal RbacPrincipal principal,
            @RequestBody FriendRequest request) {
        return blocking(() -> friendFacade.request(new RequestFriendCommand(
                imPrincipal(principal),
                request == null ? null : request.targetUserId(),
                request == null ? null : request.message()
        )));
    }

    @PostMapping("/friend-requests/{id}/accept")
    public Mono<ApiResponse<FriendRequestView>> acceptFriend(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) FriendDecisionRequest request) {
        return blocking(() -> friendFacade.accept(new DecideFriendRequestCommand(
                imPrincipal(principal), id, request == null ? null : request.reason())));
    }

    @PostMapping("/friend-requests/{id}/reject")
    public Mono<ApiResponse<FriendRequestView>> rejectFriend(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) FriendDecisionRequest request) {
        return blocking(() -> friendFacade.reject(new DecideFriendRequestCommand(
                imPrincipal(principal), id, request == null ? null : request.reason())));
    }

    @PostMapping("/friend-requests/{id}/cancel")
    public Mono<ApiResponse<FriendRequestView>> cancelFriendRequest(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long id) {
        return blocking(() -> friendFacade.cancel(new CancelFriendRequestCommand(imPrincipal(principal), id)));
    }

    @PatchMapping("/friends/{friendUserId}")
    public Mono<ApiResponse<FriendView>> updateFriendRemark(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long friendUserId,
            @RequestBody(required = false) FriendRemarkRequest request) {
        return blocking(() -> friendFacade.updateRemark(new UpdateFriendRemarkCommand(
                imPrincipal(principal), friendUserId, request == null ? null : request.remark())));
    }

    @DeleteMapping("/friends/{friendUserId}")
    public Mono<ApiResponse<Void>> removeFriend(
            @AuthenticationPrincipal RbacPrincipal principal,
            @PathVariable Long friendUserId) {
        return blockingVoid(() -> friendFacade.remove(new RemoveFriendCommand(
                imPrincipal(principal), friendUserId)));
    }

    public record FriendRequest(Long targetUserId, String message) {
    }

    public record FriendDecisionRequest(String reason) {
    }

    public record FriendRemarkRequest(String remark) {
    }
}
