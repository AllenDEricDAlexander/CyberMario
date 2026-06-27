package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

import java.time.Instant;

public record GlobalMuteCommand(
        ImPrincipal principal,
        String scopeType,
        Long scopeId,
        Long userId,
        Instant mutedUntil,
        String reason) {
}
