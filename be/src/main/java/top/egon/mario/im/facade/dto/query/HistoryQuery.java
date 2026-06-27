package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record HistoryQuery(
        ImPrincipal principal,
        Long conversationId,
        int page,
        int size,
        Long beforeSeq,
        Long afterSeq) {
}
