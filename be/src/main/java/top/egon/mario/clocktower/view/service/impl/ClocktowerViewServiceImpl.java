package top.egon.mario.clocktower.view.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerViewerMode;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.po.ClocktowerEventPo;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerVisibilityFilter;
import top.egon.mario.clocktower.event.service.ViewerContext;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerRoomResponse;
import top.egon.mario.clocktower.room.dto.response.ClocktowerSeatResponse;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.view.dto.AvailableActionResponse;
import top.egon.mario.clocktower.view.dto.ClocktowerPlayerViewResponse;
import top.egon.mario.clocktower.view.dto.PlayerSeatViewResponse;
import top.egon.mario.clocktower.view.dto.PrivateThreadSummaryResponse;
import top.egon.mario.clocktower.view.dto.PublicSeatResponse;
import top.egon.mario.clocktower.view.service.ClocktowerViewService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerViewServiceImpl implements ClocktowerViewService {

    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final ClocktowerVisibilityFilter visibilityFilter = new ClocktowerVisibilityFilter();

    @Override
    public ClocktowerPlayerViewResponse playerView(Long roomId, Long seatId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        ClocktowerSeatPo mySeat = resolveSeat(room, seatId, principal);
        ClocktowerRoomResponse roomResponse = ClocktowerRoomResponse.from(room, seats.stream()
                .map(ClocktowerSeatResponse::publicView)
                .toList());
        List<ClocktowerEventResponse> recentEvents = visibilityFilter.visibleEvents(
                eventRepository.findByRoomIdAndDeletedFalseOrderByEventSeqAsc(roomId).stream()
                        .map(this::toResponse)
                        .toList(),
                ViewerContext.player(mySeat.getId()));
        return new ClocktowerPlayerViewResponse(roomResponse, ClocktowerViewerMode.PLAYER,
                PlayerSeatViewResponse.from(mySeat),
                seats.stream().map(PublicSeatResponse::from).toList(),
                GamePhaseResponse.from(room), availableActions(room.getPhase()), recentEvents, List.of());
    }

    private ClocktowerSeatPo resolveSeat(ClocktowerRoomPo room, Long seatId, RbacPrincipal principal) {
        if (seatId != null) {
            ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(seatId, room.getId())
                    .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
            ClocktowerAccess.requireSeatOwnerOrStoryteller(room, seat, principal);
            return seat;
        }
        if (principal == null) {
            throw new ClocktowerException("CLOCKTOWER_SEAT_REQUIRED");
        }
        return seatRepository.findByRoomIdAndUserIdAndDeletedFalse(room.getId(), principal.userId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
    }

    private List<AvailableActionResponse> availableActions(ClocktowerPhase phase) {
        return switch (phase) {
            case DAY -> List.of(
                    new AvailableActionResponse("PUBLIC_SPEECH", "公开发言", true),
                    new AvailableActionResponse("NOMINATE", "提名", true)
            );
            case NOMINATION -> List.of(
                    new AvailableActionResponse("PUBLIC_SPEECH", "公开发言", true),
                    new AvailableActionResponse("VOTE", "投票", true)
            );
            case FIRST_NIGHT, NIGHT -> List.of(new AvailableActionResponse("NIGHT_CHOICE", "夜晚行动", true));
            default -> List.of();
        };
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
