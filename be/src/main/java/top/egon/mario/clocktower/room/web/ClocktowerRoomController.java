package top.egon.mario.clocktower.room.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomBoardSwitchRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomInvitationCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomMemberActionRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatClaimRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerSeatReleaseRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomInvitationResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomLobbyService;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms")
@Validated
public class ClocktowerRoomController extends ClocktowerReactiveSupport {

    private final ClocktowerRoomService roomService;
    private final ClocktowerRoomLobbyService lobbyService;

    @PostMapping
    public Mono<ApiResponse<ClocktowerRoomResponse>> create(@Valid @RequestBody ClocktowerRoomCreateRequest request,
                                                            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.createRoom(request, principal));
    }

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerRoomResponse>>> list(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.listVisibleRooms(principal));
    }

    @GetMapping("/{roomId}")
    public Mono<ApiResponse<ClocktowerRoomResponse>> get(@PathVariable Long roomId,
                                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.lobby(roomId, principal));
    }

    @PatchMapping("/{roomId}/board")
    public Mono<ApiResponse<ClocktowerRoomResponse>> switchBoard(
            @PathVariable Long roomId,
            @Valid @RequestBody ClocktowerRoomBoardSwitchRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.switchBoard(roomId, request, principal));
    }

    @PostMapping("/{roomId}/enter")
    public Mono<ApiResponse<ClocktowerRoomResponse>> enter(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.enterRoom(roomId, principal));
    }

    @PostMapping("/{roomId}/heartbeat")
    public Mono<ApiResponse<Void>> heartbeat(@PathVariable Long roomId,
                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> lobbyService.heartbeat(roomId, principal));
    }

    @PostMapping("/{roomId}/seats/{seatNo}/claim")
    public Mono<ApiResponse<ClocktowerSeatResponse>> claimSeat(
            @PathVariable Long roomId,
            @PathVariable int seatNo,
            @Valid @RequestBody(required = false) ClocktowerSeatClaimRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.claimSeat(roomId, seatNo, request, principal));
    }

    @PostMapping("/{roomId}/seats/{seatNo}/release")
    public Mono<ApiResponse<ClocktowerSeatResponse>> releaseSeat(
            @PathVariable Long roomId,
            @PathVariable int seatNo,
            @Valid @RequestBody(required = false) ClocktowerSeatReleaseRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.releaseSeat(roomId, seatNo, request, principal));
    }

    @PostMapping("/{roomId}/invitations")
    public Mono<ApiResponse<ClocktowerRoomInvitationResponse>> createInvitation(
            @PathVariable Long roomId,
            @Valid @RequestBody ClocktowerRoomInvitationCreateRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.createInvitation(roomId, request, principal));
    }

    @PostMapping("/{roomId}/invitations/{id}/accept")
    public Mono<ApiResponse<ClocktowerRoomInvitationResponse>> acceptInvitation(
            @PathVariable Long roomId,
            @PathVariable Long id,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.acceptInvitation(roomId, id, principal));
    }

    @PostMapping("/{roomId}/invitations/{id}/decline")
    public Mono<ApiResponse<ClocktowerRoomInvitationResponse>> declineInvitation(
            @PathVariable Long roomId,
            @PathVariable Long id,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> lobbyService.declineInvitation(roomId, id, principal));
    }

    @PostMapping("/{roomId}/members/{userId}/kick")
    public Mono<ApiResponse<Void>> kickMember(
            @PathVariable Long roomId,
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) ClocktowerRoomMemberActionRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> lobbyService.kickMember(roomId, userId, request, principal));
    }

    @PostMapping("/{roomId}/start")
    public Mono<ApiResponse<ClocktowerStartGameResponse>> start(@PathVariable Long roomId,
                                                                @Valid @RequestBody ClocktowerRoomStartRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roomService.start(roomId, request, principal));
    }

    @PostMapping("/{roomId}/join")
    public Mono<ApiResponse<ClocktowerSeatResponse>> join(@PathVariable Long roomId,
                                                          @Valid @RequestBody ClocktowerRoomJoinRequest request,
                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roomService.join(roomId, request, principal));
    }

    @PostMapping("/{roomId}/leave")
    public Mono<ApiResponse<Void>> leave(@PathVariable Long roomId,
                                         @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> roomService.leave(roomId, principal));
    }

    @PatchMapping("/{roomId}/seats/{seatId}")
    public Mono<ApiResponse<ClocktowerRoomResponse>> updateSeat(@PathVariable Long roomId,
                                                                @PathVariable Long seatId,
                                                                @Valid @RequestBody ClocktowerUpdateSeatRequest request,
                                                                @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roomService.updateSeat(roomId, seatId, request, principal));
    }
}
