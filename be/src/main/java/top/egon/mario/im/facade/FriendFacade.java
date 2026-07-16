package top.egon.mario.im.facade;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.CancelFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.DecideFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.UpdateFriendRemarkCommand;
import top.egon.mario.im.facade.dto.query.ListFriendRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListFriendsQuery;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.FriendView;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.service.FriendshipService;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;

import java.util.Set;

@Component
public class FriendFacade {

    private final FriendshipService friendshipService;
    private final RbacUserDirectoryFacade userDirectoryFacade;

    public FriendFacade(FriendshipService friendshipService, RbacUserDirectoryFacade userDirectoryFacade) {
        this.friendshipService = friendshipService;
        this.userDirectoryFacade = userDirectoryFacade;
    }

    public Page<UserDirectoryItemResponse> searchUsers(ImPrincipal principal, String keyword, int page, int size) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return userDirectoryFacade.search(keyword, principal.userId(), page, size);
    }

    public FriendRequestView request(RequestFriendCommand command) {
        return friendshipService.request(command);
    }

    public FriendRequestView accept(DecideFriendRequestCommand command) {
        return friendshipService.accept(command);
    }

    public FriendRequestView reject(DecideFriendRequestCommand command) {
        return friendshipService.reject(command);
    }

    public FriendRequestView cancel(CancelFriendRequestCommand command) {
        return friendshipService.cancel(command);
    }

    public void remove(RemoveFriendCommand command) {
        friendshipService.remove(command);
    }

    public FriendView updateRemark(UpdateFriendRemarkCommand command) {
        return friendshipService.updateRemark(command);
    }

    public Page<FriendView> listFriends(ListFriendsQuery query) {
        return friendshipService.listFriends(query);
    }

    public Page<FriendRequestView> listRequests(ListFriendRequestsQuery query) {
        return friendshipService.listRequests(query);
    }

    public boolean areActiveFriends(Long firstUserId, Long secondUserId) {
        return friendshipService.areActiveFriends(firstUserId, secondUserId);
    }

    public long countIncomingRequests(ImPrincipal principal) {
        return friendshipService.countIncomingRequests(principal);
    }

    public Set<Long> findActiveFriendUserIds(ImPrincipal principal) {
        return friendshipService.findActiveFriendUserIds(principal);
    }
}
