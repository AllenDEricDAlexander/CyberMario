package top.egon.mario.clocktower.replay.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.replay.dto.ClocktowerReplayResponse;
import top.egon.mario.clocktower.replay.dto.ClocktowerVoteReplayResponse;
import top.egon.mario.clocktower.replay.service.ClocktowerReplayService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerReplayServiceImpl implements ClocktowerReplayService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerEventRepository eventRepository;
    private final ClocktowerVoteRepository voteRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ClocktowerReplayResponse replay(Long roomId, String mode, Long fromSeq, Long toSeq, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        String replayMode = mode == null ? "PUBLIC" : mode;
        if ("FULL".equals(replayMode) && !canViewFull(room, principal)) {
            throw new ClocktowerException("CLOCKTOWER_REPLAY_FORBIDDEN");
        }
        List<ClocktowerEventResponse> events = eventRepository.findByRoomIdAndDeletedFalseOrderByEventSeqAsc(roomId)
                .stream()
                .filter(event -> fromSeq == null || event.getEventSeq() >= fromSeq)
                .filter(event -> toSeq == null || event.getEventSeq() <= toSeq)
                .map(this::toResponse)
                .filter(event -> "FULL".equals(replayMode) || event.visibility() == ClocktowerVisibility.PUBLIC)
                .toList();
        return new ClocktowerReplayResponse(roomId, replayMode, events);
    }

    @Override
    public List<ClocktowerVoteReplayResponse> votes(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        if (!canViewFull(room, principal)) {
            throw new ClocktowerException("CLOCKTOWER_REPLAY_FORBIDDEN");
        }
        return voteRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId).stream()
                .map(ClocktowerVoteReplayResponse::from)
                .toList();
    }

    private boolean canViewFull(ClocktowerRoomPo room, RbacPrincipal principal) {
        return ClocktowerAccess.isStoryteller(room, principal);
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
