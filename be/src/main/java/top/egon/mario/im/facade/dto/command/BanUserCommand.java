package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record BanUserCommand(
        ImPrincipal principal,
        String surfaceType,
        Long surfaceId,
        Long userId,
        String reason) {
}
