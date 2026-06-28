package top.egon.mario.im.facade.dto.query;

import top.egon.mario.im.policy.ImPrincipal;

public record AuditHistoryQuery(
        ImPrincipal principal,
        Long conversationId,
        int page,
        int size,
        Long beforeSeq,
        Long afterSeq) {

    public AuditHistoryQuery(Long conversationId, int page, int size, Long beforeSeq, Long afterSeq) {
        this(null, conversationId, page, size, beforeSeq, afterSeq);
    }
}
