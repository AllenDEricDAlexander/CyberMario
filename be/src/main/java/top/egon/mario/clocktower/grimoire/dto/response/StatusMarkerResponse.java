package top.egon.mario.clocktower.grimoire.dto.response;

import top.egon.mario.clocktower.grimoire.po.ClocktowerStatusMarkerPo;

public record StatusMarkerResponse(
        Long markerId,
        Long seatId,
        String markerType,
        String markerName,
        boolean active
) {

    public static StatusMarkerResponse from(ClocktowerStatusMarkerPo marker) {
        return new StatusMarkerResponse(marker.getId(), marker.getSeatId(), marker.getMarkerCode(),
                marker.getMarkerName(), marker.isActive());
    }
}
