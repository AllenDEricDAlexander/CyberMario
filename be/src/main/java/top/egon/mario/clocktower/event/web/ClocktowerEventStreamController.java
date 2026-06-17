package top.egon.mario.clocktower.event.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.event.service.ViewerContext;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower/rooms/{roomId}/events")
@Validated
public class ClocktowerEventStreamController {

    private final ClocktowerEventStreamService streamService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ClocktowerEventResponse>> stream(@PathVariable Long roomId,
                                                                 @RequestParam(required = false) Long seatId,
                                                                 @RequestParam(required = false) Long lastEventSeq) {
        ViewerContext viewer = ViewerContext.player(seatId);
        return streamService.stream(roomId, lastEventSeq, viewer)
                .map(event -> ServerSentEvent.<ClocktowerEventResponse>builder(event)
                        .id(String.valueOf(event.seqNo()))
                        .event(event.eventType().name())
                        .build());
    }
}
