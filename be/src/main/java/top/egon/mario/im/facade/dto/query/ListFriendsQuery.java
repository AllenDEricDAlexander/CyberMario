package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListFriendsQuery(
        ImPrincipal principal,
        int page,
        int size) {
}
