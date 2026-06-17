package top.egon.mario.clocktower.grimoire.dto.response;

public record NightStepResponse(
        int orderNo,
        Long seatId,
        String roleCode,
        String roleName,
        boolean wakeRequired,
        String skipReason,
        boolean completed
) {
}
