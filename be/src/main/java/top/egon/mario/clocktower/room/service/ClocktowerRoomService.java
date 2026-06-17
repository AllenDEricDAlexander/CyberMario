package top.egon.mario.clocktower.room.service;

import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerRoomService {

    ClocktowerRoomResponse create(ClocktowerRoomCreateRequest request, RbacPrincipal principal);

    List<ClocktowerRoomResponse> list(RbacPrincipal principal);

    ClocktowerRoomResponse get(Long roomId);

    ClocktowerStartGameResponse start(Long roomId, ClocktowerRoomStartRequest request, RbacPrincipal principal);

    ClocktowerSeatResponse join(Long roomId, ClocktowerRoomJoinRequest request, RbacPrincipal principal);

    void leave(Long roomId, RbacPrincipal principal);

    ClocktowerRoomResponse updateSeat(Long roomId, Long seatId, ClocktowerUpdateSeatRequest request,
                                      RbacPrincipal principal);
}
