package top.egon.mario.clocktower.game.mic.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicSessionView;
import top.egon.mario.clocktower.game.mic.dto.ClocktowerMicTurnView;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicTurnPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicTurnRepository;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameEventRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ClocktowerPublicMicProperties.class)
public class ClocktowerPublicMicServiceImpl implements ClocktowerPublicMicService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String GAME_STATUS_RUNNING = "RUNNING";
    private static final String PHASE_DAY = "DAY";
    private static final String SEAT_STATUS_ACTIVE = "ACTIVE";
    private static final String SESSION_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String SESSION_GRAB_MIC = "GRAB_MIC";
    private static final String SESSION_CLOSED = "CLOSED";
    private static final String STAGE_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String TURN_PENDING = "PENDING";
    private static final String TURN_ACTIVE = "ACTIVE";
    private static final String ACQUISITION_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String EVENT_MIC_SESSION_STARTED = "MIC_SESSION_STARTED";
    private static final String EVENT_MIC_TURN_STARTED = "MIC_TURN_STARTED";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventRepository gameEventRepository;
    private final ClocktowerGamePublicMicSessionRepository sessionRepository;
    private final ClocktowerGamePublicMicTurnRepository turnRepository;
    private final RoomSpaceRepository roomSpaceRepository;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final ClocktowerPublicMicProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerMicSessionView startDayMicSession(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        requireDayRunningGame(game);
        Instant now = Instant.now();
        return sessionRepository.findLockedByGameIdAndDayNo(game.getId(), game.getDayNo())
                .map(this::toView)
                .orElseGet(() -> startNewSession(game, now));
    }

    @Override
    @Transactional(readOnly = true)
    public ClocktowerMicSessionView getMicSession(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        accessPolicy.requireAuthenticated(principal);
        ClocktowerGamePublicMicSessionPo session = sessionRepository
                .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND"));
        return toView(session);
    }

    @Override
    public ClocktowerMicSessionView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_MIC_TURN_INVALID");
    }

    @Override
    public ClocktowerMicSessionView skipTurn(Long gameId, Long turnId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_MIC_TURN_INVALID");
    }

    @Override
    @Transactional(readOnly = true)
    public ClocktowerMicSessionView grabMic(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        ClocktowerGamePublicMicSessionPo session = sessionRepository
                .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND"));
        if (!SESSION_GRAB_MIC.equals(session.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
        }
        throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
    }

    @Override
    public ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
    }

    @Override
    public ClocktowerMicSessionView extendGrabMic(Long gameId, long seconds, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
    }

    @Override
    public ClocktowerMicSessionView closeSession(Long gameId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canSpeak(Long gameId, Long actorGameSeatId) {
        try {
            requireCanSpeak(gameId, actorGameSeatId);
            return true;
        } catch (ClocktowerException ex) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCanSpeak(Long gameId, Long actorGameSeatId) {
        if (actorGameSeatId == null) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        ClocktowerGamePublicMicSessionPo session = sessionRepository
                .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND"));
        if (SESSION_CLOSED.equals(session.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_CLOSED");
        }
        if (!Objects.equals(session.getCurrentHolderGameSeatId(), actorGameSeatId)) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
        ClocktowerGamePublicMicTurnPo turn = currentTurn(session);
        if (!TURN_ACTIVE.equals(turn.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
        if (turn.getExpiresAt() != null && !turn.getExpiresAt().isAfter(Instant.now())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_TURN_EXPIRED");
        }
    }

    private ClocktowerMicSessionView startNewSession(ClocktowerGamePo game, Instant now) {
        List<ClocktowerGameSeatPo> eligibleSeats = eligibleSeats(game.getId());
        if (eligibleSeats.isEmpty()) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NO_ELIGIBLE_SEATS");
        }

        ClocktowerGamePublicMicSessionPo session = new ClocktowerGamePublicMicSessionPo();
        session.setGameId(game.getId());
        session.setDayNo(game.getDayNo());
        session.setStatus(SESSION_ROUND_ROBIN);
        session.setRoundStartedAt(now);
        session = sessionRepository.saveAndFlush(session);

        List<ClocktowerGamePublicMicTurnPo> turns = new ArrayList<>();
        for (int index = 0; index < eligibleSeats.size(); index++) {
            turns.add(roundRobinTurn(session, eligibleSeats.get(index), index + 1, index == 0, now));
        }
        List<ClocktowerGamePublicMicTurnPo> savedTurns = turnRepository.saveAllAndFlush(turns);
        ClocktowerGamePublicMicTurnPo firstTurn = savedTurns.getFirst();
        session.setCurrentHolderGameSeatId(firstTurn.getGameSeatId());
        session.setCurrentTurnId(firstTurn.getId());
        sessionRepository.saveAndFlush(session);

        appendGameEvent(game, EVENT_MIC_SESSION_STARTED, now,
                Map.of("sessionId", session.getId(), "dayNo", session.getDayNo()));
        appendGameEvent(game, EVENT_MIC_TURN_STARTED, now,
                Map.of("sessionId", session.getId(), "turnId", firstTurn.getId(),
                        "gameSeatId", firstTurn.getGameSeatId(), "stage", firstTurn.getStage()));
        return toView(session);
    }

    private ClocktowerGamePublicMicTurnPo roundRobinTurn(ClocktowerGamePublicMicSessionPo session,
                                                         ClocktowerGameSeatPo seat,
                                                         int turnOrder,
                                                         boolean active,
                                                         Instant now) {
        ClocktowerGamePublicMicTurnPo turn = new ClocktowerGamePublicMicTurnPo();
        turn.setSessionId(session.getId());
        turn.setGameId(session.getGameId());
        turn.setDayNo(session.getDayNo());
        turn.setGameSeatId(seat.getId());
        turn.setTurnOrder(turnOrder);
        turn.setStage(STAGE_ROUND_ROBIN);
        turn.setAcquisitionType(ACQUISITION_ROUND_ROBIN);
        if (active) {
            turn.setStatus(TURN_ACTIVE);
            turn.setStartedAt(now);
            turn.setExpiresAt(now.plus(properties.roundRobinTurnDuration()));
        } else {
            turn.setStatus(TURN_PENDING);
        }
        return turn;
    }

    private List<ClocktowerGameSeatPo> eligibleSeats(Long gameId) {
        return gameSeatRepository.findByGameIdAndDeletedFalseOrderBySeatNoAsc(gameId).stream()
                .filter(seat -> SEAT_STATUS_ACTIVE.equals(seat.getStatus()))
                .filter(seat -> ClocktowerActorType.HUMAN.equals(seat.getActorType())
                        || ClocktowerActorType.AGENT.equals(seat.getActorType()))
                .filter(seat -> !metadataFlag(seat.getMetadataJson(), "muted"))
                .filter(seat -> !metadataFlag(seat.getMetadataJson(), "leftGame"))
                .sorted(Comparator.comparingInt(ClocktowerGameSeatPo::getSeatNo))
                .toList();
    }

    private boolean metadataFlag(String json, String field) {
        Object value = metadata(json).get(field);
        return Boolean.TRUE.equals(value);
    }

    private ClocktowerMicSessionView toView(ClocktowerGamePublicMicSessionPo session) {
        List<ClocktowerGamePublicMicTurnPo> turns = turnRepository
                .findBySessionIdAndDeletedFalseOrderByTurnOrderAscIdAsc(session.getId());
        Map<Long, ClocktowerGameSeatPo> seatsById = gameSeatRepository
                .findByGameIdAndDeletedFalseOrderBySeatNoAsc(session.getGameId())
                .stream()
                .collect(Collectors.toMap(ClocktowerGameSeatPo::getId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        List<ClocktowerMicTurnView> turnViews = turns.stream()
                .map(turn -> toTurnView(turn, seatsById.get(turn.getGameSeatId())))
                .toList();
        return new ClocktowerMicSessionView(
                session.getId(),
                session.getGameId(),
                session.getDayNo(),
                session.getStatus(),
                session.getCurrentHolderGameSeatId(),
                session.getCurrentTurnId(),
                session.getRoundStartedAt(),
                session.getRoundFinishedAt(),
                session.getGrabStartedAt(),
                session.getGrabEndsAt(),
                session.getClosedAt(),
                turnViews
        );
    }

    private ClocktowerMicTurnView toTurnView(ClocktowerGamePublicMicTurnPo turn, ClocktowerGameSeatPo seat) {
        return new ClocktowerMicTurnView(
                turn.getId(),
                turn.getGameSeatId(),
                seat == null ? null : seat.getSeatNo(),
                seat == null ? null : seat.getDisplayName(),
                seat == null ? null : seat.getActorType(),
                seat == null ? null : seat.getAgentInstanceId(),
                turn.getTurnOrder(),
                turn.getStage(),
                turn.getAcquisitionType(),
                turn.getStatus(),
                turn.getStartedAt(),
                turn.getEndedAt(),
                turn.getExpiresAt()
        );
    }

    private ClocktowerGamePublicMicTurnPo currentTurn(ClocktowerGamePublicMicSessionPo session) {
        if (session.getCurrentTurnId() == null) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
        return turnRepository.findByIdAndDeletedFalse(session.getCurrentTurnId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_TURN_NOT_FOUND"));
    }

    private void requireStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        accessPolicy.requireOwner(room, principal);
    }

    private void requireDayRunningGame(ClocktowerGamePo game) {
        if (!GAME_STATUS_RUNNING.equals(game.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_NOT_RUNNING");
        }
        if (!PHASE_DAY.equals(game.getPhase()) || game.getDayNo() <= 0) {
            throw new ClocktowerException("CLOCKTOWER_MIC_GAME_PHASE_INVALID");
        }
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private void appendGameEvent(ClocktowerGamePo game, String eventType, Instant occurredAt,
                                 Map<String, Object> payload) {
        ClocktowerGameEventPo event = new ClocktowerGameEventPo();
        event.setGameId(game.getId());
        event.setEventSeq(nextEventSeq(game.getId()));
        event.setEventType(eventType);
        event.setPhase(game.getPhase());
        event.setDayNo(game.getDayNo());
        event.setNightNo(game.getNightNo());
        event.setVisibility("PUBLIC");
        event.setVisibleGameSeatIdsJson("[]");
        event.setPayloadJson(writeJson(payload));
        event.setStatus("VISIBLE");
        event.setOccurredAt(occurredAt);
        gameEventRepository.save(event);
    }

    private long nextEventSeq(Long gameId) {
        return gameEventRepository.findTopByGameIdAndDeletedFalseOrderByEventSeqDesc(gameId)
                .map(event -> event.getEventSeq() + 1)
                .orElse(1L);
    }

    private Map<String, Object> metadata(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }
}
