package top.egon.mario.clocktower.game.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.config.ClocktowerFeatureProperties;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceRequest;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameAdvanceResult;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameFlowView;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameNightTaskSummary;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGamePhaseSignal;
import top.egon.mario.clocktower.game.flow.dto.ClocktowerGameVictoryResult;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameFlowService;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameNightTaskGateway;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGamePhaseSignalScheduler;
import top.egon.mario.clocktower.game.flow.service.ClocktowerGameVictoryService;
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;
import top.egon.mario.clocktower.game.mic.po.ClocktowerGamePublicMicSessionPo;
import top.egon.mario.clocktower.game.mic.repository.ClocktowerGamePublicMicSessionRepository;
import top.egon.mario.clocktower.game.mic.service.ClocktowerPublicMicService;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameExecutionRepository;
import top.egon.mario.clocktower.game.nomination.repository.ClocktowerGameNominationRepository;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.repository.ClocktowerGameRepository;
import top.egon.mario.clocktower.game.service.ClocktowerGameEventAppender;
import top.egon.mario.clocktower.game.service.ClocktowerGameLifecycleService;
import top.egon.mario.clocktower.room.policy.ClocktowerRoomAccessPolicy;
import top.egon.mario.clocktower.view.service.ClocktowerViewerResolver;
import top.egon.mario.rbac.service.security.RbacPrincipal;
import top.egon.mario.room.po.RoomSpacePo;
import top.egon.mario.room.repository.RoomSpaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerGameFlowServiceImpl implements ClocktowerGameFlowService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String PHASE_FIRST_NIGHT = "FIRST_NIGHT";
    private static final String PHASE_NIGHT = "NIGHT";
    private static final String PHASE_DAY = "DAY";
    private static final String PHASE_NOMINATION = "NOMINATION";
    private static final String PHASE_EXECUTION = "EXECUTION";
    private static final String PHASE_ENDED = "ENDED";
    private static final String MIC_ROUND_ROBIN = "ROUND_ROBIN";
    private static final String MIC_GRAB_MIC = "GRAB_MIC";
    private static final String MIC_CLOSED = "CLOSED";
    private static final String NOMINATION_OPEN = "OPEN";
    private static final String EXECUTION_RESOLVED = "RESOLVED";
    private static final String REASON_PENDING_NIGHT_TASKS = "PENDING_NIGHT_TASKS";
    private static final String REASON_MIC_SESSION_NOT_FOUND = "MIC_SESSION_NOT_FOUND";
    private static final String REASON_MIC_ROUND_ROBIN_NOT_FINISHED = "MIC_ROUND_ROBIN_NOT_FINISHED";
    private static final String REASON_MIC_GRAB_MIC_NOT_FINISHED = "MIC_GRAB_MIC_NOT_FINISHED";
    private static final String REASON_OPEN_NOMINATION_EXISTS = "OPEN_NOMINATION_EXISTS";
    private static final String REASON_EXECUTION_NOT_RESOLVED = "EXECUTION_NOT_RESOLVED";
    private static final String REASON_GAME_ALREADY_ENDED = "GAME_ALREADY_ENDED";
    private static final String REASON_PHASE_UNSUPPORTED = "GAME_FLOW_PHASE_UNSUPPORTED";

    private final ClocktowerGameRepository gameRepository;
    private final ClocktowerGameEventAppender eventAppender;
    private final ClocktowerGamePublicMicSessionRepository micSessionRepository;
    private final ClocktowerGameNominationRepository nominationRepository;
    private final ClocktowerGameExecutionRepository executionRepository;
    private final ClocktowerPublicMicService micService;
    private final ClocktowerGameLifecycleService lifecycleService;
    private final RoomSpaceRepository roomSpaceRepository;
    private final ClocktowerRoomAccessPolicy accessPolicy;
    private final ClocktowerViewerResolver viewerResolver;
    private final ClocktowerGameNightTaskGateway nightTaskGateway;
    private final ClocktowerGameVictoryService victoryService;
    private final ClocktowerGamePhaseSignalScheduler phaseSignalScheduler;
    private final ClocktowerFeatureProperties featureProperties;
    private final ClocktowerPublicMicProperties micProperties;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerGameFlowView getFlow(Long gameId, RbacPrincipal principal) {
        requireNewFlowEnabled();
        viewerResolver.resolveGameViewer(gameId, principal);
        ClocktowerGamePo game = gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
        return buildFlow(game);
    }

    @Override
    @Transactional
    public ClocktowerGameAdvanceResult advance(Long gameId, ClocktowerGameAdvanceRequest request,
                                               RbacPrincipal principal) {
        requireNewFlowEnabled();
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        String previousPhase = game.getPhase();
        if (gameEnded(game)) {
            throw new ClocktowerException("CLOCKTOWER_" + REASON_GAME_ALREADY_ENDED);
        }
        ClocktowerGameVictoryResult victory = victoryService.evaluate(game);
        if (victory.ended()) {
            return endForVictory(game, previousPhase, victory, principal);
        }
        ClocktowerGameFlowView flow = buildFlow(game);
        if (!flow.advanceAllowed()) {
            throw new ClocktowerException("CLOCKTOWER_" + flow.blockingReasons().getFirst());
        }
        ClocktowerGamePo advanced = applyAdvance(game, flow.nextPhase(), principal, false, null, metadata(request));
        ClocktowerGameFlowView nextFlow = buildFlow(advanced);
        return new ClocktowerGameAdvanceResult(advanced.getId(), previousPhase, advanced.getPhase(),
                true, false, nextFlow);
    }

    @Override
    @Transactional
    public ClocktowerGameAdvanceResult forceAdvance(Long gameId, ClocktowerGameAdvanceRequest request,
                                                    RbacPrincipal principal) {
        requireNewFlowEnabled();
        ClocktowerGamePo game = lockedGame(gameId);
        requireStoryteller(game, principal);
        if (gameEnded(game)) {
            throw new ClocktowerException("CLOCKTOWER_" + REASON_GAME_ALREADY_ENDED);
        }
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ClocktowerException("CLOCKTOWER_FORCE_ADVANCE_REASON_REQUIRED");
        }
        String targetPhase = request.targetPhase();
        if (!Set.of(PHASE_DAY, PHASE_NOMINATION, PHASE_NIGHT, PHASE_ENDED).contains(targetPhase)) {
            throw new ClocktowerException("CLOCKTOWER_FORCE_ADVANCE_TARGET_INVALID");
        }
        String previousPhase = game.getPhase();
        ClocktowerGamePo advanced = applyAdvance(game, targetPhase, principal, true,
                request.reason(), metadata(request));
        ClocktowerGameFlowView nextFlow = buildFlow(advanced);
        return new ClocktowerGameAdvanceResult(advanced.getId(), previousPhase, advanced.getPhase(),
                true, true, nextFlow);
    }

    private void requireNewFlowEnabled() {
        if (!featureProperties.newFlow().enabled()) {
            throw new ClocktowerException("CLOCKTOWER_NEW_FLOW_DISABLED");
        }
    }

    private ClocktowerGameFlowView buildFlow(ClocktowerGamePo game) {
        Map<String, Object> counters = new LinkedHashMap<>();
        List<String> blockingReasons = new ArrayList<>();
        String nextPhase = null;
        if (gameEnded(game)) {
            blockingReasons.add(REASON_GAME_ALREADY_ENDED);
        } else {
            ClocktowerGameVictoryResult victory = victoryService.evaluate(game);
            counters.putAll(victory.counters());
            if (victory.ended()) {
                nextPhase = PHASE_ENDED;
            } else {
                nextPhase = nextPhase(game, counters, blockingReasons);
            }
        }
        return new ClocktowerGameFlowView(game.getId(), game.getStatus(), game.getPhase(),
                game.getDayNo(), game.getNightNo(), blockingReasons.isEmpty() && nextPhase != null,
                List.copyOf(blockingReasons), nextPhase, counters);
    }

    private String nextPhase(ClocktowerGamePo game, Map<String, Object> counters, List<String> blockingReasons) {
        return switch (game.getPhase()) {
            case PHASE_FIRST_NIGHT, PHASE_NIGHT -> nextAfterNight(game, counters, blockingReasons);
            case PHASE_DAY -> nextAfterDay(game, counters, blockingReasons);
            case PHASE_NOMINATION, PHASE_EXECUTION -> nextAfterNomination(game, counters, blockingReasons);
            default -> {
                blockingReasons.add(REASON_PHASE_UNSUPPORTED);
                yield null;
            }
        };
    }

    private String nextAfterNight(ClocktowerGamePo game, Map<String, Object> counters,
                                  List<String> blockingReasons) {
        ClocktowerGameNightTaskSummary summary = nightTaskGateway.summarize(game);
        counters.put("nightMandatoryCount", summary.mandatoryCount());
        counters.put("nightPendingMandatoryCount", summary.pendingMandatoryCount());
        counters.put("nightDoneCount", summary.doneCount());
        counters.put("nightSkippedCount", summary.skippedCount());
        if (!summary.complete()) {
            blockingReasons.add(REASON_PENDING_NIGHT_TASKS);
        }
        return PHASE_DAY;
    }

    private String nextAfterDay(ClocktowerGamePo game, Map<String, Object> counters,
                                List<String> blockingReasons) {
        if (!micProperties.isEnabled()) {
            counters.put("micEnabled", false);
            counters.put("micStatus", "DISABLED");
            return PHASE_NOMINATION;
        }
        ClocktowerGamePublicMicSessionPo session = micSessionRepository
                .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .orElse(null);
        counters.put("micStatus", session == null ? null : session.getStatus());
        if (session == null) {
            blockingReasons.add(REASON_MIC_SESSION_NOT_FOUND);
        } else if (MIC_ROUND_ROBIN.equals(session.getStatus())) {
            blockingReasons.add(REASON_MIC_ROUND_ROBIN_NOT_FINISHED);
        } else if (MIC_GRAB_MIC.equals(session.getStatus())) {
            blockingReasons.add(REASON_MIC_GRAB_MIC_NOT_FINISHED);
        } else if (!MIC_CLOSED.equals(session.getStatus())) {
            blockingReasons.add(REASON_MIC_ROUND_ROBIN_NOT_FINISHED);
        }
        return PHASE_NOMINATION;
    }

    private String nextAfterNomination(ClocktowerGamePo game, Map<String, Object> counters,
                                       List<String> blockingReasons) {
        boolean openNomination = nominationRepository
                .findTopByGameIdAndStatusAndDeletedFalseOrderByIdDesc(game.getId(), NOMINATION_OPEN)
                .isPresent();
        counters.put("openNominationExists", openNomination);
        if (openNomination) {
            blockingReasons.add(REASON_OPEN_NOMINATION_EXISTS);
        }
        ClocktowerGameExecutionPo execution = executionRepository
                .findByGameIdAndDayNoAndDeletedFalse(game.getId(), game.getDayNo())
                .orElse(null);
        counters.put("executionStatus", execution == null ? null : execution.getStatus());
        if (execution == null || !EXECUTION_RESOLVED.equals(execution.getStatus())) {
            blockingReasons.add(REASON_EXECUTION_NOT_RESOLVED);
        }
        return PHASE_NIGHT;
    }

    private ClocktowerGamePo applyAdvance(ClocktowerGamePo game, String targetPhase, RbacPrincipal principal,
                                          boolean forced, String reason, Map<String, Object> metadata) {
        String previousPhase = game.getPhase();
        Instant now = Instant.now();
        if (PHASE_ENDED.equals(targetPhase)) {
            lifecycleService.endGame(game.getId(), principal);
            ClocktowerGamePo ended = findGame(game.getId());
            appendPhaseChanged(ended, previousPhase, forced, reason, metadata, now);
            schedulePhaseSignal(ended, previousPhase, forced, metadata);
            return ended;
        }
        if (PHASE_DAY.equals(targetPhase)) {
            game.setPhase(PHASE_DAY);
            if (PHASE_FIRST_NIGHT.equals(previousPhase) && game.getDayNo() <= 0) {
                game.setDayNo(1);
            } else if (PHASE_NIGHT.equals(previousPhase)) {
                game.setDayNo(game.getDayNo() + 1);
            } else if (game.getDayNo() <= 0) {
                game.setDayNo(1);
            }
        } else if (PHASE_NOMINATION.equals(targetPhase)) {
            game.setPhase(PHASE_NOMINATION);
        } else if (PHASE_NIGHT.equals(targetPhase)) {
            game.setPhase(PHASE_NIGHT);
            if (!PHASE_NIGHT.equals(previousPhase)) {
                game.setNightNo(game.getNightNo() + 1);
            }
            nightTaskGateway.initializeNightTasks(game);
        } else {
            throw new ClocktowerException("CLOCKTOWER_" + REASON_PHASE_UNSUPPORTED);
        }
        game.setLastActiveAt(now);
        ClocktowerGamePo saved = gameRepository.saveAndFlush(game);
        appendPhaseChanged(saved, previousPhase, forced, reason, metadata, now);
        if (PHASE_DAY.equals(saved.getPhase()) && micProperties.isEnabled()) {
            micService.startDayMicSession(saved.getId(), principal);
        }
        schedulePhaseSignal(saved, previousPhase, forced, metadata);
        return saved;
    }

    private ClocktowerGameAdvanceResult endForVictory(ClocktowerGamePo game,
                                                     String previousPhase,
                                                     ClocktowerGameVictoryResult victory,
                                                     RbacPrincipal principal) {
        Instant now = Instant.now();
        lifecycleService.endGame(game.getId(), principal);
        ClocktowerGamePo ended = findGame(game.getId());
        Map<String, Object> metadata = new LinkedHashMap<>(victory.counters());
        metadata.put("winner", victory.winner());
        metadata.put("victoryReason", victory.reason());
        appendPhaseChanged(ended, previousPhase, false, victory.reason(), metadata, now);
        schedulePhaseSignal(ended, previousPhase, false, metadata);
        return new ClocktowerGameAdvanceResult(ended.getId(), previousPhase, ended.getPhase(),
                true, false, buildFlow(ended));
    }

    private void appendPhaseChanged(ClocktowerGamePo game,
                                    String previousPhase,
                                    boolean forced,
                                    String reason,
                                    Map<String, Object> metadata,
                                    Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousPhase", previousPhase);
        payload.put("phase", game.getPhase());
        payload.put("dayNo", game.getDayNo());
        payload.put("nightNo", game.getNightNo());
        payload.put("forced", forced);
        payload.put("reason", reason);
        payload.put("metadata", metadata == null ? Map.of() : metadata);
        eventAppender.append(game, "PHASE_CHANGED", null, null, "PUBLIC", List.of(), payload, occurredAt);
    }

    private void schedulePhaseSignal(ClocktowerGamePo game,
                                     String previousPhase,
                                     boolean forced,
                                     Map<String, Object> metadata) {
        phaseSignalScheduler.schedule(new ClocktowerGamePhaseSignal(
                game.getId(), previousPhase, game.getPhase(), game.getDayNo(), game.getNightNo(),
                forced, metadata == null ? Map.of() : metadata));
    }

    private ClocktowerGamePo lockedGame(Long gameId) {
        if (gameId == null) {
            throw new ClocktowerException("CLOCKTOWER_GAME_ID_REQUIRED");
        }
        return gameRepository.findLockedByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private ClocktowerGamePo findGame(Long gameId) {
        return gameRepository.findByIdAndDeletedFalse(gameId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_GAME_NOT_FOUND"));
    }

    private void requireStoryteller(ClocktowerGamePo game, RbacPrincipal principal) {
        RoomSpacePo room = roomSpaceRepository.findByIdAndDeletedFalse(game.getRoomId())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        accessPolicy.requireOwner(room, principal);
    }

    private boolean gameEnded(ClocktowerGamePo game) {
        return STATUS_ENDED.equals(game.getStatus()) || PHASE_ENDED.equals(game.getPhase())
                || !STATUS_RUNNING.equals(game.getStatus());
    }

    private Map<String, Object> metadata(ClocktowerGameAdvanceRequest request) {
        if (request == null || request.metadata() == null) {
            return Map.of();
        }
        return request.metadata();
    }
}
