package top.egon.mario.clocktower.event.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventProjector;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerEventServiceImpl implements ClocktowerEventService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerEventRepository eventRepository;
    private final ClocktowerEventProjector projector;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerEventResponse append(ClocktowerEventAppendRequest request) {
        Long seqNo = eventRepository.findTopByRoomIdAndDeletedFalseOrderByEventSeqDesc(request.roomId())
                .map(event -> event.getEventSeq() + 1)
                .orElse(1L);
        ClocktowerEventPo event = new ClocktowerEventPo();
        event.setRoomId(request.roomId());
        event.setEventSeq(seqNo);
        event.setEventType(request.eventType());
        event.setPhase(request.phase());
        event.setDayNo(request.dayNo());
        event.setNightNo(request.nightNo());
        event.setActorUserId(request.actorUserId());
        event.setActorSeatId(request.actorSeatId());
        event.setTargetSeatId(request.targetSeatId());
        event.setVisibility(request.visibility());
        event.setVisibleSeatIdsJson(writeJson(request.visibleSeatIds() == null ? List.of() : request.visibleSeatIds()));
        event.setPayloadJson(writeJson(request.payload() == null ? Map.of() : request.payload()));
        ClocktowerEventPo saved = eventRepository.save(event);
        ClocktowerEventResponse response = toResponse(saved);
        projector.project(response);
        return response;
    }

    private ClocktowerEventResponse toResponse(ClocktowerEventPo event) {
        return ClocktowerEventResponse.from(event, readJson(event.getVisibleSeatIdsJson(), LONG_LIST_TYPE),
                readJson(event.getPayloadJson(), MAP_TYPE));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_EVENT_JSON_INVALID", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_EVENT_JSON_INVALID", e);
        }
    }
}
