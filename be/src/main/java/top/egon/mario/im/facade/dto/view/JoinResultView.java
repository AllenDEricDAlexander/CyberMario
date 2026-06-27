package top.egon.mario.im.facade.dto.view;

public record JoinResultView(
        String status,
        String surfaceType,
        Long surfaceId,
        Long membershipId,
        Long joinRequestId) {
}
