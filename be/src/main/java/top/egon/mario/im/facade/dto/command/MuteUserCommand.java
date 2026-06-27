package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

import java.time.Instant;

public record MuteUserCommand(
        ImPrincipal principal,
        String surfaceType,
        Long surfaceId,
        Long userId,
        Instant mutedUntil,
        String reason) {
}
