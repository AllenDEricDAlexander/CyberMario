package top.egon.mario.clocktower.view.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerGameProjectionMapper {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String VISIBILITY_STORYTELLER = "STORYTELLER";
    private static final String VISIBILITY_AUDIT = "AUDIT";

    private final ObjectMapper objectMapper;

    public ClocktowerGameEventResponse toEventResponse(ClocktowerGameEventPo event) {
        return ClocktowerGameEventResponse.from(event, readJson(event.getVisibleGameSeatIdsJson(), LONG_LIST_TYPE),
                readJson(event.getPayloadJson(), MAP_TYPE));
    }

    public boolean visibleTo(ClocktowerGameEventResponse event, ClocktowerViewerContext viewer,
                             boolean includeAudit) {
        if (VISIBILITY_PUBLIC.equals(event.visibility())) {
            return true;
        }
        if (viewer.viewerMode() == ClocktowerViewerMode.ADMIN_AUDIT) {
            return includeAudit || !VISIBILITY_AUDIT.equals(event.visibility());
        }
        if (VISIBILITY_AUDIT.equals(event.visibility())) {
            return false;
        }
        if (viewer.viewerMode() == ClocktowerViewerMode.STORYTELLER) {
            return VISIBILITY_PRIVATE.equals(event.visibility()) || VISIBILITY_STORYTELLER.equals(event.visibility());
        }
        return viewer.viewerMode() == ClocktowerViewerMode.PLAYER
                && VISIBILITY_PRIVATE.equals(event.visibility())
                && event.visibleGameSeatIds().contains(viewer.gameSeatId());
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_GAME_EVENT_JSON_INVALID", e);
        }
    }
}
