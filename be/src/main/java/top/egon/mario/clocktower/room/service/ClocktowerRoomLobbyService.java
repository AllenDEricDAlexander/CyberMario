package top.egon.mario.clocktower.room.service;

import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomBoardSwitchRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomInvitationCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomMemberActionRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatReleaseRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomInvitationResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerRoomLobbyService {

    ClocktowerRoomResponse createRoom(ClocktowerRoomCreateRequest request, RbacPrincipal principal);

    List<ClocktowerRoomResponse> listVisibleRooms(RbacPrincipal principal);

    ClocktowerRoomResponse lobby(Long roomId, RbacPrincipal principal);

    ClocktowerRoomResponse switchBoard(Long roomId, ClocktowerRoomBoardSwitchRequest request,
                                       RbacPrincipal principal);

    ClocktowerRoomResponse enterRoom(Long roomId, RbacPrincipal principal);

    void heartbeat(Long roomId, RbacPrincipal principal);

    ClocktowerSeatResponse claimSeat(Long roomId, int seatNo, ClocktowerSeatClaimRequest request,
                                     RbacPrincipal principal);

    ClocktowerSeatResponse releaseSeat(Long roomId, int seatNo, ClocktowerSeatReleaseRequest request,
                                       RbacPrincipal principal);

    ClocktowerRoomInvitationResponse createInvitation(Long roomId, ClocktowerRoomInvitationCreateRequest request,
                                                      RbacPrincipal principal);

    ClocktowerRoomInvitationResponse acceptInvitation(Long roomId, Long invitationId, RbacPrincipal principal);

    ClocktowerRoomInvitationResponse declineInvitation(Long roomId, Long invitationId, RbacPrincipal principal);

    void kickMember(Long roomId, Long userId, ClocktowerRoomMemberActionRequest request, RbacPrincipal principal);
}
