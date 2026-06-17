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
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomCreateRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerRoomJoinRequest;
import top.egon.mario.clocktower.room.dto.request.ClocktowerUpdateSeatRequest;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.service.ClocktowerRoomService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms")
@Validated
public class ClocktowerRoomController {

    private final ClocktowerRoomService roomService;

    @PostMapping
    public Mono<ClocktowerRoomResponse> create(@Valid @RequestBody ClocktowerRoomCreateRequest request,
                                               @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> roomService.create(request, principal));
    }

    @GetMapping
    public Mono<List<ClocktowerRoomResponse>> list(@AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> roomService.list(principal));
    }

    @GetMapping("/{roomId}")
    public Mono<ClocktowerRoomResponse> get(@PathVariable Long roomId) {
        return Mono.fromSupplier(() -> roomService.get(roomId));
    }

    @PostMapping("/{roomId}/join")
    public Mono<ClocktowerSeatResponse> join(@PathVariable Long roomId,
                                             @Valid @RequestBody ClocktowerRoomJoinRequest request,
                                             @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> roomService.join(roomId, request, principal));
    }

    @PostMapping("/{roomId}/leave")
    public Mono<Void> leave(@PathVariable Long roomId,
                            @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromRunnable(() -> roomService.leave(roomId, principal));
    }

    @PatchMapping("/{roomId}/seats/{seatId}")
    public Mono<ClocktowerRoomResponse> updateSeat(@PathVariable Long roomId,
                                                   @PathVariable Long seatId,
                                                   @Valid @RequestBody ClocktowerUpdateSeatRequest request,
                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return Mono.fromSupplier(() -> roomService.updateSeat(roomId, seatId, request, principal));
    }
}
