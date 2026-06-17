package top.egon.mario.clocktower.event.service;

import reactor.core.publisher.Flux;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

import java.util.List;

public interface ClocktowerEventStreamService {

    List<ClocktowerEventResponse> backfill(Long roomId, Long lastEventSeq, ViewerContext viewer);

    Flux<ClocktowerEventResponse> stream(Long roomId, Long lastEventSeq, ViewerContext viewer);

    void publish(ClocktowerEventResponse event);
}
