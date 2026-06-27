package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListConversationsQuery(
        ImPrincipal principal,
        String contextType,
        Long contextId) {
}
