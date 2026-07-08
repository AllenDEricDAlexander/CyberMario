package top.egon.mario.clocktower.game.nomination.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResolveRequest;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameExecutionResponse;
import top.egon.mario.clocktower.game.nomination.dto.ClocktowerGameNominationResponse;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameExecutionRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameVoteRepository;
import top.egon.mario.clocktower.game.nomination.service.ClocktowerGameExecutionService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.repository.ClocktowerGameSeatRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerGameExecutionServiceImpl implements ClocktowerGameExecutionService {

    private static final String GAME_STATUS_RUNNING = "RUNNING";
    private static final String PHASE_EXECUTION = "EXECUTION";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String LIFE_DEAD = "DEAD";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameSeatRepository gameSeatRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameVoteRepository voteRepository;
    private final ClocktowerGameExecutionRepository executionRepository;
    private final ClocktowerGameEventAppender eventAppender;
    private final RoomSpaceRepository roomSpaceRepository;
    private final ClocktowerRoomAccessPolicy accessPolicy;

    @Override
    @Transactional
    public ClocktowerGameNominationResponse closeNomination(Long gameId, Long nominationId, RbacPrincipal principal) {
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        requireRunningGame(game);
        ClocktowerGameNominationPo nomination = nominationRepository
                .findLockedByIdAndGameIdAndDeletedFalse(nominationId, game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        if (!STATUS_OPEN.equals(nomination.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_OPEN");
        }

        Instant now = Instant.now();
        int voteCount = Math.toIntExact(voteRepository.countByNominationIdAndVoteValueTrueAndDeletedFalse(
                nomination.getId()));
        nomination.setStatus(STATUS_CLOSED);
        nomination.setOpenGameId(null);
        nomination.setVoteCount(voteCount);
        nomination.setClosedAt(now);
        nomination = nominationRepository.saveAndFlush(nomination);

        ClocktowerGameExecutionPo execution = recomputeExecutionCandidate(game, now);
        appendNominationClosed(game, nomination, now);
        appendCandidateUpdated(game, execution, now);
        return ClocktowerGameNominationResponse.from(nomination, ClocktowerGameExecutionResponse.from(execution));
    }

    @Override
    @Transactional
    public ClocktowerGameExecutionResponse resolveExecution(Long gameId,
                                                           ClocktowerGameExecutionResolveRequest request,
                                                           RbacPrincipal principal) {
        if (request == null || request.execute() == null) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_RESOLVE_REQUEST_REQUIRED");
        }
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        requireRunningGame(game);
        ClocktowerGameExecutionPo execution = executionRepository
                .findLockedByGameIdAndDayNo(game.getId(), game.getDayNo())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_EXECUTION_NOT_FOUND"));
        if (STATUS_RESOLVED.equals(execution.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_ALREADY_RESOLVED");
        }

        Instant now = Instant.now();
        game.setPhase(PHASE_EXECUTION);
        gameRepository.saveAndFlush(game);
        if (Boolean.TRUE.equals(request.execute())) {
            resolveExecutionCandidate(game, execution, request, now);
        } else {
            resolveNoExecution(game, execution, now);
        }
        execution.setStatus(STATUS_RESOLVED);
        execution.setResolvedAt(now);
        execution = executionRepository.saveAndFlush(execution);
        return ClocktowerGameExecutionResponse.from(execution);
    }

    private ClocktowerGameExecutionPo recomputeExecutionCandidate(ClocktowerGamePo game, Instant now) {
        List<ClocktowerGameNominationPo> qualifying = nominationRepository
                .findByGameIdAndDayNoAndStatusAndDeletedFalseOrderByIdAsc(game.getId(), game.getDayNo(), STATUS_CLOSED)
                .stream()
                .filter(nomination -> nomination.getVoteCount() >= nomination.getRequiredVotes())
                .toList();
        ClocktowerGameNominationPo candidate = uniqueTopCandidate(qualifying);
        ClocktowerGameExecutionPo execution = executionRepository.findLockedByGameIdAndDayNo(game.getId(),
                        game.getDayNo())
                .orElseGet(() -> {
                    ClocktowerGameExecutionPo created = new ClocktowerGameExecutionPo();
                    created.setGameId(game.getId());
                    created.setDayNo(game.getDayNo());
                    created.setMetadataJson("{}");
                    return created;
                });
        execution.setNomineeGameSeatId(candidate == null ? null : candidate.getNomineeGameSeatId());
        execution.setNominationId(candidate == null ? null : candidate.getId());
        execution.setStatus(STATUS_PENDING);
        execution.setExecuted(false);
        execution.setResolvedAt(null);
        return executionRepository.saveAndFlush(execution);
    }

    private ClocktowerGameNominationPo uniqueTopCandidate(List<ClocktowerGameNominationPo> qualifying) {
        if (qualifying.isEmpty()) {
            return null;
        }
        int topVoteCount = qualifying.stream()
                .map(ClocktowerGameNominationPo::getVoteCount)
                .max(Comparator.naturalOrder())
                .orElse(0);
        List<ClocktowerGameNominationPo> top = qualifying.stream()
                .filter(nomination -> nomination.getVoteCount() == topVoteCount)
                .toList();
        return top.size() == 1 ? top.getFirst() : null;
    }

    private void resolveExecutionCandidate(ClocktowerGamePo game,
                                           ClocktowerGameExecutionPo execution,
                                           ClocktowerGameExecutionResolveRequest request,
                                           Instant now) {
        if (execution.getNomineeGameSeatId() == null || execution.getNominationId() == null) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_REQUIRED");
        }
        if (!Objects.equals(execution.getNomineeGameSeatId(), request.targetGameSeatId())
                || !Objects.equals(execution.getNominationId(), request.nominationId())) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_MISMATCH");
        }
        ClocktowerGameSeatPo target = gameSeatRepository
                .findByIdAndGameIdAndDeletedFalse(execution.getNomineeGameSeatId(), game.getId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_EXECUTION_TARGET_NOT_FOUND"));
        target.setLifeStatus(LIFE_DEAD);
        target.setPublicLifeStatus(LIFE_DEAD);
        gameSeatRepository.saveAndFlush(target);
        execution.setExecuted(true);

        Map<String, Object> payload = executionPayload(execution);
        if (request.note() != null) {
            payload.put("note", request.note());
        }
        eventAppender.append(game, "PLAYER_EXECUTED", null, target.getId(), "PUBLIC", List.of(), payload, now);
        eventAppender.append(game, "PLAYER_DIED", null, target.getId(), "PUBLIC", List.of(), payload, now);
    }

    private void resolveNoExecution(ClocktowerGamePo game, ClocktowerGameExecutionPo execution, Instant now) {
        if (execution.getNomineeGameSeatId() != null || execution.getNominationId() != null) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_EXISTS");
        }
        execution.setExecuted(false);
        eventAppender.append(game, "NO_EXECUTION", null, null, "PUBLIC", List.of(), executionPayload(execution), now);
    }

    private void appendNominationClosed(ClocktowerGamePo game, ClocktowerGameNominationPo nomination, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nominationId", nomination.getId());
        payload.put("nominatorGameSeatId", nomination.getNominatorGameSeatId());
        payload.put("nomineeGameSeatId", nomination.getNomineeGameSeatId());
        payload.put("voteCount", nomination.getVoteCount());
        payload.put("requiredVotes", nomination.getRequiredVotes());
        eventAppender.append(game, "NOMINATION_CLOSED", null, nomination.getNomineeGameSeatId(), "PUBLIC", List.of(),
                payload, now);
    }

    private void appendCandidateUpdated(ClocktowerGamePo game, ClocktowerGameExecutionPo execution, Instant now) {
        eventAppender.append(game, "EXECUTION_CANDIDATE_UPDATED", null, execution.getNomineeGameSeatId(),
                "PUBLIC", List.of(), executionPayload(execution), now);
    }

    private Map<String, Object> executionPayload(ClocktowerGameExecutionPo execution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("executionId", execution.getId());
        payload.put("nominationId", execution.getNominationId());
        payload.put("candidateGameSeatId", execution.getNomineeGameSeatId());
        payload.put("status", execution.getStatus());
        payload.put("executed", execution.isExecuted());
        return payload;
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private void requireStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        accessPolicy.requireOwner(room, principal);
    }

    private void requireRunningGame(ClocktowerGamePo game) {
        if (!GAME_STATUS_RUNNING.equals(game.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_GAME_NOT_RUNNING");
        }
    }
}
