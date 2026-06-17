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
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.clocktower.common.web.ClocktowerReactiveSupport;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomStartRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerStartGameResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms")
@Validated
public class ClocktowerRoomController extends ClocktowerReactiveSupport {

    private final ClocktowerRoomService roomService;

    @PostMapping
    public Mono<ApiResponse<ClocktowerRoomResponse>> create(@Valid @RequestBody ClocktowerRoomCreateRequest request,
                                                            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roomService.create(request, principal));
    }

    @GetMapping
    public Mono<ApiResponse<List<ClocktowerRoomResponse>>> list(@AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> roomService.list(principal));
    }

    @GetMapping("/{roomId}")
    public Mono<ApiResponse<ClocktowerRoomResponse>> get(@PathVariable Long roomId) {
        return blocking(() -> roomService.get(roomId));
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
