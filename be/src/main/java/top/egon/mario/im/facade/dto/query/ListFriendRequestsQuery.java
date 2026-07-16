package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListFriendRequestsQuery(
        ImPrincipal principal,
        String box,
        int page,
        int size) {
}
