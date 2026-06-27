package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record CreateChannelCommand(
        ImPrincipal principal,
        String contextType,
        Long contextId,
        String channelKey,
        String name,
        String joinPolicy,
        String metadataJson) {
}
