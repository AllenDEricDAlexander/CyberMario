package top.egon.mario.clocktower.view.service;

import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.room.po.RoomSpacePo;

public record ClocktowerViewerContext(
        ClocktowerGamePo game,
        RoomSpacePo room,
        ClocktowerRoomProfilePo profile,
        ClocktowerGameSeatPo gameSeat,
        ClocktowerViewerMode viewerMode
) {

    public Long gameSeatId() {
        return gameSeat == null ? null : gameSeat.getId();
    }
}
