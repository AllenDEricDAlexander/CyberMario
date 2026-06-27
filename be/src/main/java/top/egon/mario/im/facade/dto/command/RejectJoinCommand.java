package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record RejectJoinCommand(
        ImPrincipal principal,
        Long joinRequestId,
        String reason) {
}
