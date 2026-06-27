package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record CancelJoinCommand(
        ImPrincipal principal,
        Long joinRequestId) {
}
