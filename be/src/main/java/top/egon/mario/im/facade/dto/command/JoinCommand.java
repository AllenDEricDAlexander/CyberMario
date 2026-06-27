package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record JoinCommand(
        ImPrincipal principal,
        String surfaceType,
        Long surfaceId,
        String reason) {
}
