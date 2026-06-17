package top.egon.mario.clocktower.event.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.event.service.ClocktowerVisibilityFilter;
import top.egon.mario.clocktower.event.service.ViewerContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class ClocktowerEventStreamServiceImpl implements ClocktowerEventStreamService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final ClocktowerVisibilityFilter visibilityFilter = new ClocktowerVisibilityFilter();
    private final ConcurrentMap<Long, Sinks.Many<ClocktowerEventResponse>> roomSinks = new ConcurrentHashMap<>();

    @Override
    public List<ClocktowerEventResponse> backfill(Long roomId, Long lastEventSeq, ViewerContext viewer) {
        Long seq = lastEventSeq == null ? 0L : lastEventSeq;
        return eventRepository.findByRoomIdAndEventSeqGreaterThanAndDeletedFalseOrderByEventSeqAsc(roomId, seq)
                .stream()
                .map(this::toResponse)
                .filter(event -> visibilityFilter.isVisible(event, viewer))
                .toList();
    }

    @Override
    public Flux<ClocktowerEventResponse> stream(Long roomId, Long lastEventSeq, ViewerContext viewer) {
        Flux<ClocktowerEventResponse> backfill = Flux.fromIterable(backfill(roomId, lastEventSeq, viewer));
        Flux<ClocktowerEventResponse> live = roomSinks.computeIfAbsent(roomId,
                        id -> Sinks.many().multicast().directBestEffort())
                .asFlux()
                .filter(event -> visibilityFilter.isVisible(event, viewer));
        return backfill.concatWith(live);
    }

    @Override
    public void publish(ClocktowerEventResponse event) {
        roomSinks.computeIfAbsent(event.roomId(), id -> Sinks.many().multicast().directBestEffort())
                .tryEmitNext(event);
    }

    private ClocktowerEventResponse toResponse(ClocktowerEventPo event) {
        return ClocktowerEventResponse.from(event, readJson(event.getVisibleSeatIdsJson(), LONG_LIST_TYPE),
                readJson(event.getPayloadJson(), MAP_TYPE));
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_EVENT_JSON_INVALID", e);
        }
    }
}
