package top.egon.mario.clocktower.event.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.event.service.ViewerContext;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}/events")
@Validated
public class ClocktowerEventStreamController {

    private final ClocktowerEventStreamService streamService;
    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ClocktowerEventResponse>> stream(@PathVariable Long roomId,
                                                                 @RequestParam(required = false) Long seatId,
                                                                 @RequestParam(required = false) Long lastEventSeq,
                                                                 @AuthenticationPrincipal RbacPrincipal principal) {
        ViewerContext viewer = resolveViewer(roomId, seatId, principal);
        return streamService.stream(roomId, lastEventSeq, viewer)
                .map(event -> ServerSentEvent.<ClocktowerEventResponse>builder(event)
                        .id(String.valueOf(event.seqNo()))
                        .event(event.eventType().name())
                        .build());
    }

    private ViewerContext resolveViewer(Long roomId, Long seatId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        if (ClocktowerAccess.isStoryteller(room, principal)) {
            return ViewerContext.storyteller(principal.userId());
        }
        ClocktowerAccess.requireAuthenticated(principal);
        if (seatId == null) {
            Long principalSeatId = seatRepository.findByRoomIdAndUserIdAndDeletedFalse(roomId, principal.userId())
                    .map(ClocktowerSeatPo::getId)
                    .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
            return ViewerContext.player(principalSeatId);
        }
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(seatId, roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        ClocktowerAccess.requireSeatOwnerOrStoryteller(room, seat, principal);
        return ViewerContext.player(seat.getId());
    }
}
