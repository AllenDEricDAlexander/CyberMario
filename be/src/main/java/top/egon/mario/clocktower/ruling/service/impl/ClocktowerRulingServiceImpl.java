package top.egon.mario.clocktower.ruling.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.grimoire.dto.response.ClocktowerGrimoireResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingApplyResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingResponse;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingUndoRequest;
import top.egon.mario.clocktower.ruling.po.ClocktowerRulingPo;
import top.egon.mario.clocktower.ruling.repository.ClocktowerRulingRepository;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClocktowerRulingServiceImpl implements ClocktowerRulingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerNominationRepository nominationRepository;
    private final ClocktowerRulingRepository rulingRepository;
    private final ClocktowerEventService eventService;
    private final ObjectMapper objectMapper;
    private final ClocktowerGrimoireService grimoireService;

    @Override
    @Transactional
    public ClocktowerRulingApplyResponse create(Long roomId, ClocktowerRulingCreateRequest request,
                                                RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        validate(request);
        ClocktowerRulingPo ruling = newRuling(room, request);
        ruling.setSnapshotJson(snapshot(room, request));
        List<ClocktowerEventResponse> events = apply(room, ruling, request, principal);
        ruling.setEventIdsJson(writeJson(events.stream().map(ClocktowerEventResponse::eventId).toList()));
        ClocktowerRulingPo saved = rulingRepository.save(ruling);
        ClocktowerGrimoireResponse grimoire = grimoireService.getGrimoire(roomId, principal);
        return new ClocktowerRulingApplyResponse(ClocktowerRulingResponse.from(saved), grimoire, events);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerRulingResponse> list(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        return rulingRepository.findByRoomIdAndDeletedFalseOrderByIdDesc(roomId).stream()
                .map(ClocktowerRulingResponse::from)
                .toList();
    }

    @Override
    public ClocktowerRulingApplyResponse undo(Long roomId, Long rulingId, ClocktowerRulingUndoRequest request,
                                              RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_RULING_UNDO_NOT_READY");
    }

    private void validate(ClocktowerRulingCreateRequest request) {
        if (request.rulingType() == null) {
            throw new ClocktowerException("CLOCKTOWER_RULING_TYPE_REQUIRED");
        }
        if (request.reason() == null) {
            throw new ClocktowerException("CLOCKTOWER_RULING_REASON_REQUIRED");
        }
        if (!supported(request.rulingType())) {
            throw new ClocktowerException("CLOCKTOWER_RULING_TYPE_NOT_SUPPORTED");
        }
        if (request.rulingType() == ClocktowerRulingType.SET_PUBLIC_LIFE) {
            validatePublicLifeStatus(request.publicLifeStatus());
        }
        validateWinner(request.winner());
        boolean highRisk = request.rulingType() == ClocktowerRulingType.SET_PUBLIC_LIFE
                || request.rulingType() == ClocktowerRulingType.END_GAME;
        if (highRisk && !StringUtils.hasText(request.note())) {
            throw new ClocktowerException("CLOCKTOWER_RULING_NOTE_REQUIRED");
        }
    }

    private boolean supported(ClocktowerRulingType rulingType) {
        return rulingType == ClocktowerRulingType.MARK_DEAD
                || rulingType == ClocktowerRulingType.RESTORE_ALIVE
                || rulingType == ClocktowerRulingType.SET_PUBLIC_LIFE
                || rulingType == ClocktowerRulingType.EXECUTE_PLAYER
                || rulingType == ClocktowerRulingType.SKIP_EXECUTION
                || rulingType == ClocktowerRulingType.END_GAME
                || rulingType == ClocktowerRulingType.CLOSE_NOMINATION
                || rulingType == ClocktowerRulingType.REOPEN_NOMINATION
                || rulingType == ClocktowerRulingType.VOID_NOMINATION;
    }

    private void validatePublicLifeStatus(String publicLifeStatus) {
        if (!StringUtils.hasText(publicLifeStatus)) {
            throw new ClocktowerException("CLOCKTOWER_PUBLIC_LIFE_STATUS_REQUIRED");
        }
        if (!"ALIVE".equals(publicLifeStatus) && !"DEAD".equals(publicLifeStatus)) {
            throw new ClocktowerException("CLOCKTOWER_PUBLIC_LIFE_STATUS_INVALID");
        }
    }

    private void validateWinner(String winner) {
        if (StringUtils.hasText(winner) && !"GOOD".equals(winner) && !"EVIL".equals(winner)) {
            throw new ClocktowerException("CLOCKTOWER_RULING_WINNER_INVALID");
        }
    }

    private ClocktowerRulingPo newRuling(ClocktowerRoomPo room, ClocktowerRulingCreateRequest request) {
        ClocktowerRulingPo ruling = new ClocktowerRulingPo();
        ruling.setRoomId(room.getId());
        ruling.setRulingType(request.rulingType());
        ruling.setStatus(ClocktowerRulingStatus.APPLIED);
        ruling.setTargetSeatId(request.targetSeatId());
        ruling.setNominationId(request.nominationId());
        ruling.setTargetPhase(request.targetPhase());
        ruling.setPublicLifeStatus(request.publicLifeStatus());
        ruling.setWinner(request.winner());
        ruling.setReason(request.reason());
        ruling.setNote(text(request.note()));
        ruling.setPublicNote(request.publicNote());
        ruling.setVisibility(request.visibility() == null ? ClocktowerVisibility.PUBLIC : request.visibility());
        return ruling;
    }

    private List<ClocktowerEventResponse> apply(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                ClocktowerRulingCreateRequest request, RbacPrincipal principal) {
        return switch (request.rulingType()) {
            case MARK_DEAD -> applyLife(room, ruling, principal, "DEAD", "DEAD", ClocktowerEventType.PLAYER_DIED);
            case RESTORE_ALIVE -> applyLife(room, ruling, principal, "ALIVE", "ALIVE",
                    ClocktowerEventType.STORYTELLER_RULING);
            case SET_PUBLIC_LIFE -> applyPublicLife(room, ruling, request, principal);
            case EXECUTE_PLAYER -> applyExecution(room, ruling, principal);
            case SKIP_EXECUTION -> applyNominationStatus(room, ruling, principal, "CLOSED",
                    ClocktowerEventType.STORYTELLER_RULING);
            case END_GAME -> applyEndGame(room, ruling, principal);
            case CLOSE_NOMINATION -> applyNominationStatus(room, ruling, principal, "CLOSED",
                    ClocktowerEventType.STORYTELLER_RULING);
            case REOPEN_NOMINATION -> applyNominationStatus(room, ruling, principal, "OPEN",
                    ClocktowerEventType.STORYTELLER_RULING);
            case VOID_NOMINATION -> applyNominationStatus(room, ruling, principal, "VOID",
                    ClocktowerEventType.STORYTELLER_RULING);
            default -> throw new ClocktowerException("CLOCKTOWER_RULING_TYPE_NOT_SUPPORTED");
        };
    }

    private List<ClocktowerEventResponse> applyLife(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                    RbacPrincipal principal, String realStatus, String publicStatus,
                                                    ClocktowerEventType eventType) {
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setLifeStatus(realStatus);
        seat.setPublicLifeStatus(publicStatus);
        seatRepository.save(seat);
        return List.of(append(room, principal, ruling.getTargetSeatId(), eventType, ruling));
    }

    private List<ClocktowerEventResponse> applyPublicLife(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                          ClocktowerRulingCreateRequest request,
                                                          RbacPrincipal principal) {
        if (!StringUtils.hasText(request.publicLifeStatus())) {
            throw new ClocktowerException("CLOCKTOWER_PUBLIC_LIFE_STATUS_REQUIRED");
        }
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setPublicLifeStatus(request.publicLifeStatus());
        seatRepository.save(seat);
        return List.of(append(room, principal, ruling.getTargetSeatId(), ClocktowerEventType.STORYTELLER_RULING, ruling));
    }

    private List<ClocktowerEventResponse> applyExecution(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                         RbacPrincipal principal) {
        ClocktowerSeatPo seat = seatRepository.findByIdAndRoomIdAndDeletedFalse(ruling.getTargetSeatId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
        seat.setLifeStatus("DEAD");
        seat.setPublicLifeStatus("DEAD");
        seatRepository.save(seat);
        if (ruling.getNominationId() != null) {
            ClocktowerNominationPo nomination = nominationRepository
                    .findByIdAndRoomIdAndDeletedFalse(ruling.getNominationId(), room.getId())
                    .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
            nomination.setExecuted(true);
            nomination.setStatus("CLOSED");
            nominationRepository.save(nomination);
        }
        List<ClocktowerEventResponse> events = new ArrayList<>();
        events.add(append(room, principal, seat.getId(), ClocktowerEventType.PLAYER_EXECUTED, ruling));
        events.add(append(room, principal, seat.getId(), ClocktowerEventType.PLAYER_DIED, ruling));
        return events;
    }

    private List<ClocktowerEventResponse> applyNominationStatus(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                                RbacPrincipal principal, String status,
                                                                ClocktowerEventType eventType) {
        ClocktowerNominationPo nomination = nominationRepository
                .findByIdAndRoomIdAndDeletedFalse(ruling.getNominationId(), room.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        nomination.setStatus(status);
        nominationRepository.save(nomination);
        return List.of(append(room, principal, nomination.getNomineeSeatId(), eventType, ruling));
    }

    private List<ClocktowerEventResponse> applyEndGame(ClocktowerRoomPo room, ClocktowerRulingPo ruling,
                                                       RbacPrincipal principal) {
        room.setStatus(ClocktowerRoomStatus.ENDED);
        room.setPhase(ClocktowerPhase.ENDED);
        roomRepository.save(room);
        return List.of(append(room, principal, null, ClocktowerEventType.GAME_ENDED, ruling));
    }

    private ClocktowerEventResponse append(ClocktowerRoomPo room, RbacPrincipal principal, Long targetSeatId,
                                           ClocktowerEventType eventType, ClocktowerRulingPo ruling) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("publicNote", publicText(ruling));
        if (ruling.getVisibility() == ClocktowerVisibility.PUBLIC) {
            if (StringUtils.hasText(ruling.getWinner())) {
                payload.put("winner", ruling.getWinner());
            }
        } else {
            payload.put("rulingType", ruling.getRulingType().name());
            payload.put("reason", ruling.getReason().name());
            payload.put("winner", ruling.getWinner());
        }
        return eventService.append(new ClocktowerEventAppendRequest(room.getId(), eventType, room.getPhase(),
                room.getCurrentDayNo(), room.getCurrentNightNo(), principal == null ? null : principal.userId(),
                null, targetSeatId, ruling.getVisibility(), List.of(), payload));
    }

    private String snapshot(ClocktowerRoomPo room, ClocktowerRulingCreateRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("roomStatus", room.getStatus().name());
        snapshot.put("roomPhase", room.getPhase().name());
        snapshot.put("currentDayNo", room.getCurrentDayNo());
        snapshot.put("currentNightNo", room.getCurrentNightNo());
        if (request.targetSeatId() != null) {
            seatRepository.findByIdAndRoomIdAndDeletedFalse(request.targetSeatId(), room.getId()).ifPresent(seat -> {
                snapshot.put("seatId", seat.getId());
                snapshot.put("lifeStatus", seat.getLifeStatus());
                snapshot.put("publicLifeStatus", seat.getPublicLifeStatus());
                snapshot.put("hasDeadVote", seat.isHasDeadVote());
            });
        }
        if (request.nominationId() != null) {
            nominationRepository.findById(request.nominationId())
                    .filter(nomination -> !nomination.isDeleted() && nomination.getRoomId().equals(room.getId()))
                    .ifPresent(nomination -> {
                        snapshot.put("nominationId", nomination.getId());
                        snapshot.put("nominationStatus", nomination.getStatus());
                        snapshot.put("voteCount", nomination.getVoteCount());
                        snapshot.put("executed", nomination.isExecuted());
                    });
        }
        return writeJson(snapshot);
    }

    private String publicText(ClocktowerRulingPo ruling) {
        if (StringUtils.hasText(ruling.getPublicNote())) {
            return ruling.getPublicNote();
        }
        return switch (ruling.getRulingType()) {
            case MARK_DEAD -> "一名玩家死亡";
            case RESTORE_ALIVE -> "一名玩家复活";
            case END_GAME -> "游戏结束";
            default -> "说书人裁定";
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_RULING_JSON_INVALID", e);
        }
    }

    private Map<String, Object> readSnapshot(ClocktowerRulingPo ruling) {
        try {
            return objectMapper.readValue(ruling.getSnapshotJson(), MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("CLOCKTOWER_RULING_JSON_INVALID", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
