package top.egon.mario.clocktower.grimoire.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.dto.request.StorytellerActionRequest;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GrimoireSeatResponse;
import top.egon.mario.clocktower.grimoire.dto.response.NightChecklistResponse;
import top.egon.mario.clocktower.grimoire.dto.response.NightStepResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StatusMarkerResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerActionResponse;
import top.egon.mario.clocktower.grimoire.dto.response.StorytellerTaskResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerGrimoireEntryPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStatusMarkerPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerGrimoireEntryRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStatusMarkerRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClocktowerGrimoireServiceImpl implements ClocktowerGrimoireService {

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerGrimoireEntryRepository grimoireEntryRepository;
    private final ClocktowerStatusMarkerRepository markerRepository;
    private final ClocktowerStorytellerTaskRepository taskRepository;
    private final ClocktowerNightOrderRepository nightOrderRepository;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerEventService eventService;

    @Override
    public ClocktowerGrimoireResponse getGrimoire(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        Map<Long, ClocktowerGrimoireEntryPo> entries = grimoireEntryRepository
                .findByRoomIdAndDeletedFalseOrderBySeatIdAsc(roomId)
                .stream()
                .collect(Collectors.toMap(ClocktowerGrimoireEntryPo::getSeatId, Function.identity(), (left, right) -> left));
        return new ClocktowerGrimoireResponse(roomId, GamePhaseResponse.from(room),
                seats.stream().map(seat -> GrimoireSeatResponse.from(seat, entries.get(seat.getId()))).toList(),
                markerRepository.findByRoomIdAndActiveTrueAndDeletedFalseOrderByIdAsc(roomId).stream()
                        .map(StatusMarkerResponse::from)
                        .toList(),
                List.of(),
                taskRepository.findByRoomIdAndStatusAndDeletedFalseOrderBySortOrderAsc(roomId, "PENDING").stream()
                        .map(StorytellerTaskResponse::from)
                        .toList(),
                false);
    }

    @Override
    public NightChecklistResponse nightChecklist(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(roomId);
        Map<String, ClocktowerSeatPo> seatByRole = seats.stream()
                .filter(seat -> seat.getRoleCode() != null)
                .collect(Collectors.toMap(ClocktowerSeatPo::getRoleCode, Function.identity(), (left, right) -> left));
        String nightType = room.getCurrentNightNo() <= 1 ? "FIRST_NIGHT" : "OTHER_NIGHT";
        List<ClocktowerNightOrderPo> orders = nightOrderRepository
                .findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(
                        room.getScriptCode(), nightType, seatByRole.keySet());
        Map<String, ClocktowerRolePo> roleByCode = roleRepository.findByRoleCodeInAndDeletedFalse(seatByRole.keySet())
                .stream()
                .collect(Collectors.toMap(ClocktowerRolePo::getRoleCode, Function.identity(), (left, right) -> left));
        List<NightStepResponse> steps = orders.stream()
                .map(order -> toNightStep(order, seatByRole.get(order.getRoleCode()), roleByCode.get(order.getRoleCode())))
                .toList();
        return new NightChecklistResponse(room.getCurrentNightNo(), nightType, steps,
                !steps.isEmpty() && steps.stream().allMatch(NightStepResponse::completed));
    }

    @Override
    @Transactional
    public StorytellerActionResponse storytellerAction(Long roomId, StorytellerActionRequest request,
                                                       RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        return switch (request.actionType()) {
            case "ADD_MARKER" -> addMarker(room, request, principal);
            case "REMOVE_MARKER" -> removeMarker(room, request, principal);
            case "MARK_DEAD" -> updateLife(room, request, principal, "DEAD", ClocktowerEventType.PLAYER_DIED);
            case "RESTORE_ALIVE" -> updateLife(room, request, principal, "ALIVE", ClocktowerEventType.STORYTELLER_RULING);
            case "PUBLIC_ANNOUNCEMENT" -> append(room, principal, null, ClocktowerEventType.PUBLIC_MESSAGE_SENT,
                    ClocktowerVisibility.PUBLIC, List.of(), Map.of("content", text(request.note())));
            case "PRIVATE_INFO" -> privateInfo(room, request, principal);
            case "ADVANCE_PHASE" -> advancePhase(room, request, principal);
            case "CHANGE_ROLE", "CHANGE_ALIGNMENT", "RESOLVE_TASK" ->
                    StorytellerActionResponse.rejected("ACTION_NOT_ENABLED_IN_PHASE_ONE", getGrimoire(roomId, principal));
            default -> StorytellerActionResponse.rejected("UNKNOWN_ACTION_TYPE", getGrimoire(roomId, principal));
        };
    }

    private static NightStepResponse toNightStep(ClocktowerNightOrderPo order, ClocktowerSeatPo seat,
                                                 ClocktowerRolePo role) {
        return new NightStepResponse(order.getOrderNo(), seat == null ? null : seat.getId(), order.getRoleCode(),
                role == null ? order.getRoleCode() : role.getName(), true, null, false);
    }

    private StorytellerActionResponse addMarker(ClocktowerRoomPo room, StorytellerActionRequest request,
                                                RbacPrincipal principal) {
        Long targetSeatId = firstTarget(request);
        ClocktowerStatusMarkerPo marker = new ClocktowerStatusMarkerPo();
        marker.setRoomId(room.getId());
        marker.setSeatId(targetSeatId);
        marker.setMarkerCode(stringPayload(request, "markerType", "MARKER"));
        marker.setMarkerName(stringPayload(request, "markerName", marker.getMarkerCode()));
        marker.setMarkerSource("STORYTELLER");
        marker.setPayloadJson("{}");
        markerRepository.save(marker);
        return append(room, principal, targetSeatId, ClocktowerEventType.MARKER_ADDED, ClocktowerVisibility.STORYTELLER,
                List.of(), Map.of("markerType", marker.getMarkerCode(), "note", text(request.note())));
    }

    private StorytellerActionResponse removeMarker(ClocktowerRoomPo room, StorytellerActionRequest request,
                                                   RbacPrincipal principal) {
        Long markerId = longPayload(request, "markerId");
        if (markerId == null) {
            return StorytellerActionResponse.rejected("MARKER_ID_REQUIRED", getGrimoire(room.getId(), principal));
        }
        ClocktowerStatusMarkerPo marker = markerRepository.findByIdAndRoomIdAndDeletedFalse(markerId, room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MARKER_NOT_FOUND"));
        marker.setActive(false);
        markerRepository.save(marker);
        return append(room, principal, marker.getSeatId(), ClocktowerEventType.MARKER_REMOVED,
                ClocktowerVisibility.STORYTELLER, List.of(), Map.of("markerId", markerId));
    }

    private StorytellerActionResponse updateLife(ClocktowerRoomPo room, StorytellerActionRequest request,
                                                 RbacPrincipal principal, String lifeStatus,
                                                 ClocktowerEventType eventType) {
        Long targetSeatId = firstTarget(request);
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(targetSeatId, room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setLifeStatus(lifeStatus);
        seatRepository.save(seat);
        return append(room, principal, targetSeatId, eventType, ClocktowerVisibility.PUBLIC, List.of(),
                Map.of("lifeStatus", lifeStatus, "note", text(request.note())));
    }

    private StorytellerActionResponse privateInfo(ClocktowerRoomPo room, StorytellerActionRequest request,
                                                  RbacPrincipal principal) {
        Long targetSeatId = firstTarget(request);
        return append(room, principal, targetSeatId, ClocktowerEventType.PRIVATE_MESSAGE_SENT,
                ClocktowerVisibility.PRIVATE, List.of(targetSeatId), Map.of("content", text(request.note())));
    }

    private StorytellerActionResponse advancePhase(ClocktowerRoomPo room, StorytellerActionRequest request,
                                                   RbacPrincipal principal) {
        ClocktowerPhase targetPhase = targetPhase(room.getPhase(), request);
        room.setPhase(targetPhase);
        if (targetPhase == ClocktowerPhase.DAY && room.getCurrentDayNo() == 0) {
            room.setCurrentDayNo(1);
        }
        if (targetPhase == ClocktowerPhase.NIGHT) {
            room.setCurrentNightNo(room.getCurrentNightNo() + 1);
        }
        roomRepository.save(room);
        return append(room, principal, null, ClocktowerEventType.PHASE_CHANGED, ClocktowerVisibility.PUBLIC,
                List.of(), Map.of("phase", targetPhase.name()));
    }

    private StorytellerActionResponse append(ClocktowerRoomPo room, RbacPrincipal principal, Long targetSeatId,
                                             ClocktowerEventType eventType, ClocktowerVisibility visibility,
                                             List<Long> visibleSeatIds, Map<String, Object> payload) {
        ClocktowerEventResponse event = eventService.append(new ClocktowerEventAppendRequest(room.getId(), eventType,
                room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo(),
                principal == null ? null : principal.userId(), null, targetSeatId, visibility, visibleSeatIds, payload));
        return StorytellerActionResponse.accepted(event, getGrimoire(room.getId(), principal));
    }

    private static ClocktowerPhase targetPhase(ClocktowerPhase currentPhase, StorytellerActionRequest request) {
        String requested = stringPayload(request, "targetPhase", null);
        if (requested != null) {
            return ClocktowerPhase.valueOf(requested);
        }
        return switch (currentPhase) {
            case FIRST_NIGHT, NIGHT -> ClocktowerPhase.DAY;
            case DAY -> ClocktowerPhase.NIGHT;
            case NOMINATION -> ClocktowerPhase.EXECUTION;
            case EXECUTION -> ClocktowerPhase.NIGHT;
            default -> ClocktowerPhase.DAY;
        };
    }

    private static Long firstTarget(StorytellerActionRequest request) {
        if (request.targetSeatIds() == null || request.targetSeatIds().isEmpty()) {
            throw new ClocktowerException("CLOCKTOWER_ACTION_TARGET_REQUIRED");
        }
        return request.targetSeatIds().getFirst();
    }

    private static String stringPayload(StorytellerActionRequest request, String key, String defaultValue) {
        if (request.payload() == null || !request.payload().containsKey(key)) {
            return defaultValue;
        }
        Object value = request.payload().get(key);
        return value == null ? defaultValue : value.toString();
    }

    private static Long longPayload(StorytellerActionRequest request, String key) {
        if (request.payload() == null || !request.payload().containsKey(key)) {
            return null;
        }
        Object value = request.payload().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
