package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record ListSurfaceMembersQuery(
        ImPrincipal principal,
        String surfaceType,
        Long surfaceId,
        int page,
        int size) {
}
