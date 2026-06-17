package top.egon.mario.clocktower.event.service;

import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;

public record ViewerContext(Long userId, Long seatId, ClocktowerViewerMode mode) {

    public static ViewerContext player(Long seatId) {
        return new ViewerContext(null, seatId, ClocktowerViewerMode.PLAYER);
    }

    public static ViewerContext storyteller(Long userId) {
        return new ViewerContext(userId, null, ClocktowerViewerMode.STORYTELLER);
    }

    public static ViewerContext admin(Long userId) {
        return new ViewerContext(userId, null, ClocktowerViewerMode.ADMIN);
    }
}
