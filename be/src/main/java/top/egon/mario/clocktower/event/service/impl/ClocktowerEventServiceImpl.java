package top.egon.mario.clocktower.event.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventProjector;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.event.service.ClocktowerEventStreamService;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerEventServiceImpl implements ClocktowerEventService {

    private static final int APPEND_RETRY_LIMIT = 3;
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerEventRepository eventRepository;
    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerEventProjector projector;
    private final ObjectMapper objectMapper;
    private final ClocktowerEventStreamService streamService;

    @Override
    @Transactional
    public ClocktowerEventResponse append(ClocktowerEventAppendRequest request) {
        for (int attempt = 1; attempt <= APPEND_RETRY_LIMIT; attempt++) {
            try {
                roomRepository.findLockedByIdAndDeletedFalse(request.roomId())
                        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
                return appendOnce(request);
            } catch (DataIntegrityViolationException ex) {
                if (attempt == APPEND_RETRY_LIMIT) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("CLOCKTOWER_EVENT_APPEND_RETRY_EXHAUSTED");
    }

    private ClocktowerEventResponse appendOnce(ClocktowerEventAppendRequest request) {
        Long seqNo = nextSeqNo(request.roomId());
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
        publishAfterCommit(response);
        return response;
    }

    private Long nextSeqNo(Long roomId) {
        return eventRepository.findTopByRoomIdAndDeletedFalseOrderByEventSeqDesc(roomId)
                .map(event -> event.getEventSeq() + 1)
                .orElse(1L);
    }

    private void publishAfterCommit(ClocktowerEventResponse response) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            streamService.publish(response);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                streamService.publish(response);
            }
        });
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
