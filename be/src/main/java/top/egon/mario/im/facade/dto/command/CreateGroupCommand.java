package top.egon.mario.im.facade.dto.command;

import top.egon.mario.im.policy.ImPrincipal;

public record CreateGroupCommand(
        ImPrincipal principal,
        Long channelId,
        String contextType,
        Long contextId,
        String groupKey,
        String name,
        String joinPolicy,
        String metadataJson) {
}
