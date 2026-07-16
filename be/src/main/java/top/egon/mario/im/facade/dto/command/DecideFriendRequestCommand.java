package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record DecideFriendRequestCommand(
        ImPrincipal principal,
        Long friendshipId,
        String reason) {
}
