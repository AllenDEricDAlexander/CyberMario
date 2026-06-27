package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListGroupsQuery(
        ImPrincipal principal,
        Long channelId,
        String contextType,
        Long contextId) {
}
