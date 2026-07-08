package top.egon.mario.clocktower.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerGameEventAppender {

    private final ClocktowerGameEventRepository gameEventRepository;
    private final ObjectMapper objectMapper;

    public ClocktowerGameEventResponse append(ClocktowerGamePo game,
                                              String eventType,
                                              Long actorGameSeatId,
                                              Long targetGameSeatId,
                                              String visibility,
                                              List<Long> visibleGameSeatIds,
                                              Map<String, Object> payload,
                                              Instant occurredAt) {
        List<Long> visibleSeatIds = visibleGameSeatIds == null ? List.of() : visibleGameSeatIds;
        Map<String, Object> eventPayload = payload == null ? Map.of() : payload;
        ClocktowerGameEventPo event = new ClocktowerGameEventPo();
        event.setGameId(game.getId());
        event.setEventSeq(nextEventSeq(game.getId()));
        event.setEventType(eventType);
        event.setPhase(game.getPhase());
        event.setDayNo(game.getDayNo());
        event.setNightNo(game.getNightNo());
        event.setActorGameSeatId(actorGameSeatId);
        event.setTargetGameSeatId(targetGameSeatId);
        event.setVisibility(visibility);
        event.setVisibleGameSeatIdsJson(writeJson(visibleSeatIds));
        event.setPayloadJson(writeJson(eventPayload));
        event.setStatus("VISIBLE");
        event.setOccurredAt(occurredAt);
        ClocktowerGameEventPo saved = gameEventRepository.saveAndFlush(event);
        return ClocktowerGameEventResponse.from(saved, visibleSeatIds, eventPayload);
    }

    private long nextEventSeq(Long gameId) {
        return gameEventRepository.findTopByGameIdAndDeletedFalseOrderByEventSeqDesc(gameId)
                .map(event -> event.getEventSeq() + 1)
                .orElse(1L);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_GAME_EVENT_JSON_INVALID");
        }
    }
}
