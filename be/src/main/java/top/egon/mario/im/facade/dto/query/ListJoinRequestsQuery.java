package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListJoinRequestsQuery(
        ImPrincipal principal,
        String surfaceType,
        Long surfaceId,
        int page,
        int size) {
}
