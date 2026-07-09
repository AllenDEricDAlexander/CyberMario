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
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.view.dto.ClocktowerGameEventResponse;
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
    private static final String TURN_DONE = "DONE";
    private static final String TURN_SKIPPED = "SKIPPED";
    private static final String TURN_EXPIRED = "EXPIRED";
    private static final String TURN_CANCELLED = "CANCELLED";
    private static final String ACQUISITION_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String ACQUISITION_GRAB = "GRAB";
    private static final String EVENT_MIC_SESSION_STARTED = "MIC_SESSION_STARTED";
    private static final String EVENT_MIC_TURN_STARTED = "MIC_TURN_STARTED";
    private static final String EVENT_MIC_TURN_FINISHED = "MIC_TURN_FINISHED";
    private static final String EVENT_MIC_TURN_SKIPPED = "MIC_TURN_SKIPPED";
    private static final String EVENT_MIC_TURN_SKIPPED_BY_ST = "MIC_TURN_SKIPPED_BY_ST";
    private static final String EVENT_MIC_TURN_EXPIRED = "MIC_TURN_EXPIRED";
    private static final String EVENT_MIC_GRAB_OPENED = "MIC_GRAB_OPENED";
    private static final String EVENT_MIC_SESSION_EXTENDED = "MIC_SESSION_EXTENDED";
    private static final String EVENT_MIC_SESSION_EXTENDED_BY_ST = "MIC_SESSION_EXTENDED_BY_ST";
    private static final String EVENT_MIC_SESSION_CLOSED = "MIC_SESSION_CLOSED";
    private static final String EVENT_MIC_SESSION_CLOSED_BY_ST = "MIC_SESSION_CLOSED_BY_ST";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameEventAppender gameEventAppender;
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
    @Transactional
    public ClocktowerMicSessionView getMicSession(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        accessPolicy.requireAuthenticated(principal);
        ClocktowerGamePublicMicSessionPo session = sessionRepository
                .findLockedByGameIdAndDayNo(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND"));
        refreshExpiredState(game, session, Instant.now());
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView finishCurrentTurn(Long gameId, Long turnId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        requireSessionOpen(session);
        ClocktowerGamePublicMicTurnPo turn = requireTurn(session, turnId);
        if (!TURN_ACTIVE.equals(turn.getStatus()) || !Objects.equals(session.getCurrentTurnId(), turn.getId())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_TURN_INVALID");
        }
        requireCurrentHolderOrStoryteller(game, turn, principal);
        finishActiveTurn(game, session, turn, now, TURN_DONE, EVENT_MIC_TURN_FINISHED);
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView finishCurrentTurnAsActor(Long gameId, Long actorGameSeatId) {
        ClocktowerGamePo game = lockedGame(gameId);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        requireSessionOpen(session);
        ClocktowerGamePublicMicTurnPo turn = currentTurn(session);
        if (!TURN_ACTIVE.equals(turn.getStatus())
                || !Objects.equals(session.getCurrentHolderGameSeatId(), actorGameSeatId)
                || !Objects.equals(turn.getGameSeatId(), actorGameSeatId)) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
        finishActiveTurn(game, session, turn, now, TURN_DONE, EVENT_MIC_TURN_FINISHED);
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView skipTurn(Long gameId, Long turnId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        requireSessionOpen(session);
        ClocktowerGamePublicMicTurnPo turn = requireTurn(session, turnId);
        if (!TURN_PENDING.equals(turn.getStatus()) && !TURN_ACTIVE.equals(turn.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_TURN_INVALID");
        }
        turn.setStatus(TURN_SKIPPED);
        turn.setEndedAt(now);
        ClocktowerGameEventResponse event = appendGameEvent(game, EVENT_MIC_TURN_SKIPPED_BY_ST, now,
                turnPayload(session, turn));
        turn.setSpeechEventId(event.eventId());
        if (Objects.equals(session.getCurrentTurnId(), turn.getId())) {
            session.setCurrentHolderGameSeatId(null);
            session.setCurrentTurnId(null);
            advanceAfterRoundRobinTurn(game, session, now);
        }
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView grabMic(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        ClocktowerMicSessionView unavailable = unavailableGrabMicResult(game, session, now);
        if (unavailable != null) {
            return unavailable;
        }
        ClocktowerGameSeatPo seat = requireHumanPlayerSeat(game, principal);
        return grabMicForSeat(game, session, seat, now);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView grabMicAsActor(Long gameId, Long actorGameSeatId) {
        ClocktowerGamePo game = lockedGame(gameId);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        ClocktowerGameSeatPo seat = gameSeatRepository.findByIdAndGameIdAndDeletedFalse(actorGameSeatId, game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        if (!ClocktowerActorType.HUMAN.equals(seat.getActorType())
                && !ClocktowerActorType.AGENT.equals(seat.getActorType())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_ACTOR_INVALID");
        }
        return grabMicForSeat(game, session, seat, now);
    }

    private ClocktowerMicSessionView grabMicForSeat(ClocktowerGamePo game,
                                                    ClocktowerGamePublicMicSessionPo session,
                                                    ClocktowerGameSeatPo seat,
                                                    Instant now) {
        ClocktowerMicSessionView unavailable = unavailableGrabMicResult(game, session, now);
        if (unavailable != null) {
            return unavailable;
        }
        if (!SEAT_STATUS_ACTIVE.equals(seat.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_SEAT_INACTIVE");
        }
        ClocktowerGamePublicMicTurnPo turn = new ClocktowerGamePublicMicTurnPo();
        turn.setSessionId(session.getId());
        turn.setGameId(session.getGameId());
        turn.setDayNo(session.getDayNo());
        turn.setGameSeatId(seat.getId());
        turn.setTurnOrder(nextTurnOrder(session.getId()));
        turn.setStage(SESSION_GRAB_MIC);
        turn.setAcquisitionType(ACQUISITION_GRAB);
        turn.setStatus(TURN_ACTIVE);
        turn.setStartedAt(now);
        turn.setExpiresAt(earlier(now.plus(properties.grabMicHoldDuration()), session.getGrabEndsAt()));
        turn = turnRepository.saveAndFlush(turn);
        session.setCurrentHolderGameSeatId(turn.getGameSeatId());
        session.setCurrentTurnId(turn.getId());
        appendGameEvent(game, EVENT_MIC_TURN_STARTED, now, turnPayload(session, turn));
        return toView(session);
    }

    private ClocktowerMicSessionView unavailableGrabMicResult(ClocktowerGamePo game,
                                                              ClocktowerGamePublicMicSessionPo session,
                                                              Instant now) {
        requireSessionOpen(session);
        if (!SESSION_GRAB_MIC.equals(session.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
        }
        if (session.getGrabEndsAt() == null || !session.getGrabEndsAt().isAfter(now)) {
            closeSessionInternal(game, session, now, EVENT_MIC_SESSION_CLOSED);
            return toView(session);
        }
        if (session.getCurrentHolderGameSeatId() != null) {
            throw new ClocktowerException("CLOCKTOWER_MIC_OCCUPIED");
        }
        return null;
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView releaseMic(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        requireSessionOpen(session);
        ClocktowerGamePublicMicTurnPo turn = currentTurn(session);
        requireCurrentHolderOrStoryteller(game, turn, principal);
        finishActiveTurn(game, session, turn, now, TURN_DONE, EVENT_MIC_TURN_FINISHED);
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView extendGrabMic(Long gameId, long seconds, RbacPrincipal principal) {
        if (seconds <= 0) {
            throw new ClocktowerException("CLOCKTOWER_MIC_EXTEND_SECONDS_INVALID");
        }
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        Instant now = Instant.now();
        refreshExpiredState(game, session, now);
        requireSessionOpen(session);
        if (!SESSION_GRAB_MIC.equals(session.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_GRAB_NOT_OPEN");
        }
        session.setGrabEndsAt(session.getGrabEndsAt().plusSeconds(seconds));
        appendGameEvent(game, EVENT_MIC_SESSION_EXTENDED_BY_ST, now,
                Map.of("sessionId", session.getId(), "seconds", seconds, "grabEndsAt", session.getGrabEndsAt()));
        return toView(session);
    }

    @Override
    @Transactional
    public ClocktowerMicSessionView closeSession(Long gameId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        ClocktowerGamePublicMicSessionPo session = lockedCurrentSession(game);
        closeSessionInternal(game, session, Instant.now(), EVENT_MIC_SESSION_CLOSED_BY_ST);
        return toView(session);
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

    private void refreshExpiredState(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session, Instant now) {
        if (SESSION_CLOSED.equals(session.getStatus())) {
            return;
        }
        activeTurn(session).ifPresent(turn -> {
            if (turn.getExpiresAt() != null && !turn.getExpiresAt().isAfter(now)) {
                expireActiveTurn(game, session, turn, now);
            }
        });
        if (SESSION_GRAB_MIC.equals(session.getStatus())
                && session.getCurrentTurnId() == null
                && session.getGrabEndsAt() != null
                && !session.getGrabEndsAt().isAfter(now)) {
            closeSessionInternal(game, session, now, EVENT_MIC_SESSION_CLOSED);
        }
    }

    private void expireActiveTurn(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session,
                                  ClocktowerGamePublicMicTurnPo turn, Instant now) {
        turn.setStatus(TURN_EXPIRED);
        turn.setEndedAt(now);
        ClocktowerGameEventResponse event = appendGameEvent(game, EVENT_MIC_TURN_EXPIRED, now,
                turnPayload(session, turn));
        turn.setSpeechEventId(event.eventId());
        session.setCurrentHolderGameSeatId(null);
        session.setCurrentTurnId(null);
        if (STAGE_ROUND_ROBIN.equals(turn.getStage())) {
            advanceAfterRoundRobinTurn(game, session, now);
        } else if (SESSION_GRAB_MIC.equals(session.getStatus())
                && session.getGrabEndsAt() != null
                && !session.getGrabEndsAt().isAfter(now)) {
            closeSessionInternal(game, session, now, EVENT_MIC_SESSION_CLOSED);
        }
    }

    private void finishActiveTurn(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session,
                                  ClocktowerGamePublicMicTurnPo turn, Instant now, String status, String eventType) {
        turn.setStatus(status);
        turn.setEndedAt(now);
        ClocktowerGameEventResponse event = appendGameEvent(game, eventType, now, turnPayload(session, turn));
        turn.setSpeechEventId(event.eventId());
        session.setCurrentHolderGameSeatId(null);
        session.setCurrentTurnId(null);
        if (STAGE_ROUND_ROBIN.equals(turn.getStage())) {
            advanceAfterRoundRobinTurn(game, session, now);
        }
    }

    private void advanceAfterRoundRobinTurn(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session,
                                            Instant now) {
        turnRepository.findFirstBySessionIdAndStatusAndDeletedFalseOrderByTurnOrderAscIdAsc(
                        session.getId(), TURN_PENDING)
                .ifPresentOrElse(turn -> activateTurn(game, session, turn, now), () -> enterGrabMic(game, session, now));
    }

    private void activateTurn(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session,
                              ClocktowerGamePublicMicTurnPo turn, Instant now) {
        turn.setStatus(TURN_ACTIVE);
        turn.setStartedAt(now);
        turn.setExpiresAt(now.plus(properties.roundRobinTurnDuration()));
        session.setCurrentHolderGameSeatId(turn.getGameSeatId());
        session.setCurrentTurnId(turn.getId());
        appendGameEvent(game, EVENT_MIC_TURN_STARTED, now, turnPayload(session, turn));
    }

    private void enterGrabMic(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session, Instant now) {
        session.setStatus(SESSION_GRAB_MIC);
        session.setCurrentHolderGameSeatId(null);
        session.setCurrentTurnId(null);
        session.setRoundFinishedAt(now);
        session.setGrabStartedAt(now);
        session.setGrabEndsAt(now.plus(properties.grabMicTotalDuration()));
        appendGameEvent(game, EVENT_MIC_GRAB_OPENED, now,
                Map.of("sessionId", session.getId(), "grabEndsAt", session.getGrabEndsAt()));
    }

    private void closeSessionInternal(ClocktowerGamePo game, ClocktowerGamePublicMicSessionPo session, Instant now,
                                      String eventType) {
        if (SESSION_CLOSED.equals(session.getStatus())) {
            return;
        }
        activeTurn(session).ifPresent(turn -> {
            turn.setStatus(TURN_CANCELLED);
            turn.setEndedAt(now);
        });
        session.setStatus(SESSION_CLOSED);
        session.setCurrentHolderGameSeatId(null);
        session.setCurrentTurnId(null);
        session.setClosedAt(now);
        appendGameEvent(game, eventType, now, Map.of("sessionId", session.getId()));
    }

    private void requireSessionOpen(ClocktowerGamePublicMicSessionPo session) {
        if (SESSION_CLOSED.equals(session.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_SESSION_CLOSED");
        }
    }

    private ClocktowerGamePublicMicSessionPo lockedCurrentSession(ClocktowerGamePo game) {
        return sessionRepository.findLockedByGameIdAndDayNo(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_SESSION_NOT_FOUND"));
    }

    private ClocktowerGamePublicMicTurnPo requireTurn(ClocktowerGamePublicMicSessionPo session, Long turnId) {
        ClocktowerGamePublicMicTurnPo turn = turnRepository.findByIdAndDeletedFalse(turnId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_TURN_NOT_FOUND"));
        if (!Objects.equals(turn.getSessionId(), session.getId())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_TURN_INVALID");
        }
        return turn;
    }

    private java.util.Optional<ClocktowerGamePublicMicTurnPo> activeTurn(ClocktowerGamePublicMicSessionPo session) {
        if (session.getCurrentTurnId() != null) {
            return turnRepository.findByIdAndDeletedFalse(session.getCurrentTurnId())
                    .filter(turn -> TURN_ACTIVE.equals(turn.getStatus()));
        }
        return turnRepository.findFirstBySessionIdAndStatusAndDeletedFalseOrderByStartedAtDescIdDesc(
                session.getId(), TURN_ACTIVE);
    }

    private void requireCurrentHolderOrStoryteller(ClocktowerGamePo game, ClocktowerGamePublicMicTurnPo turn,
                                                   RbacPrincipal principal) {
        if (isStoryteller(game, principal)) {
            return;
        }
        ClocktowerGameSeatPo holder = gameSeatRepository.findById(turn.getGameSeatId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_SEAT_NOT_FOUND"));
        if (principal == null || principal.userId() == null
                || !ClocktowerActorType.HUMAN.equals(holder.getActorType())
                || !Objects.equals(holder.getUserId(), principal.userId())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_NOT_HOLDER");
        }
    }

    private ClocktowerGameSeatPo requireHumanPlayerSeat(ClocktowerGamePo game, RbacPrincipal principal) {
        accessPolicy.requireAuthenticated(principal);
        ClocktowerGameSeatPo seat = gameSeatRepository
                .findByGameIdAndUserIdAndDeletedFalse(game.getId(), principal.userId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_MIC_PLAYER_REQUIRED"));
        if (!SEAT_STATUS_ACTIVE.equals(seat.getStatus()) || !ClocktowerActorType.HUMAN.equals(seat.getActorType())) {
            throw new ClocktowerException("CLOCKTOWER_MIC_PLAYER_REQUIRED");
        }
        return seat;
    }

    private boolean isStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return false;
        }
        return roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .map(room -> Objects.equals(room.getOwnerUserId(), principal.userId()))
                .orElse(false);
    }

    private int nextTurnOrder(Long sessionId) {
        return turnRepository.findBySessionIdAndDeletedFalseOrderByTurnOrderAscIdAsc(sessionId).stream()
                .mapToInt(ClocktowerGamePublicMicTurnPo::getTurnOrder)
                .max()
                .orElse(0) + 1;
    }

    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private Map<String, Object> turnPayload(ClocktowerGamePublicMicSessionPo session,
                                            ClocktowerGamePublicMicTurnPo turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("turnId", turn.getId());
        payload.put("gameSeatId", turn.getGameSeatId());
        payload.put("stage", turn.getStage());
        payload.put("status", turn.getStatus());
        return payload;
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

    private ClocktowerGameEventResponse appendGameEvent(ClocktowerGamePo game, String eventType, Instant occurredAt,
                                                        Map<String, Object> payload) {
        return gameEventAppender.append(game, eventType, null, null, "PUBLIC", List.of(), payload, occurredAt);
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

}
