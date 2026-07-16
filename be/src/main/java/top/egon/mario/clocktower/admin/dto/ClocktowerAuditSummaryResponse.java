package top.egon.mario.clocktower.admin.dto;

public record ClocktowerAuditSummaryResponse(
        long roomCount,
        long gameCount,
        long eventCount,
        long conversationCount,
        long messageCount,
        long memberCount,
        long invitationCount,
        long banCount) {
}
