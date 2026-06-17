package top.egon.mario.clocktower.view.dto;

public record PrivateThreadSummaryResponse(
        Long threadId,
        Long seatId,
        String displayName,
        int unreadCount
) {
}
