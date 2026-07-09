package top.egon.mario.clocktower.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerEventType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerVisibility;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.repository.ClocktowerEventRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.clocktower.flow.dto.ClocktowerExecutionDeathPolicy;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionCandidateResponse;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.NightTaskSummaryResponse;
import top.egon.mario.clocktower.flow.dto.NominationSummaryResponse;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.clocktower.flow.dto.VictoryCandidateResponse;
import top.egon.mario.clocktower.flow.service.ClocktowerFlowService;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.grimoire.service.ClocktowerGrimoireService;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.ruling.dto.ClocktowerRulingCreateRequest;
import top.egon.mario.clocktower.ruling.service.ClocktowerRulingService;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Legacy room flow service kept for pre-GAME_V2 rooms during the cutover window.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class ClocktowerFlowServiceImpl implements ClocktowerFlowService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String NOMINATION_OPEN = "OPEN";
    private static final String NOMINATION_CLOSED = "CLOSED";

    private final ClocktowerRoomRepository roomRepository;
    private final ClocktowerSeatRepository seatRepository;
    private final ClocktowerStorytellerTaskRepository taskRepository;
    private final ClocktowerNominationRepository nominationRepository;
    private final ClocktowerVoteRepository voteRepository;
    private final ClocktowerRoleRepository roleRepository;
    private final ClocktowerEventService eventService;
    private final ClocktowerEventRepository eventRepository;
    private final ClocktowerRulingService rulingService;
    private final ClocktowerGrimoireService grimoireService;
    private final ClocktowerRuleEngine ruleEngine;

    @Override
    @Transactional
    public ClocktowerFlowResponse getFlow(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = room(roomId);
        ClocktowerAccess.requireStoryteller(room, principal);
        ensureNightTasks(room, principal);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse advance(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        ensureNightTasks(room, principal);
        ClocktowerFlowResponse flow = buildFlow(room);
        if (!flow.advanceAllowed()) {
            throw new ClocktowerException(flow.blockingReasons().getFirst());
        }
        switch (flow.nextTransition()) {
            case COMPLETE_FIRST_NIGHT -> {
                room.setPhase(ClocktowerPhase.DAY);
                room.setCurrentDayNo(1);
            }
            case COMPLETE_NIGHT -> {
                room.setPhase(ClocktowerPhase.DAY);
                room.setCurrentDayNo(room.getCurrentDayNo() + 1);
            }
            case START_NOMINATION -> room.setPhase(ClocktowerPhase.NOMINATION);
            case START_EXECUTION -> room.setPhase(ClocktowerPhase.EXECUTION);
            case START_NIGHT -> {
                room.setPhase(ClocktowerPhase.NIGHT);
                room.setCurrentNightNo(room.getCurrentNightNo() + 1);
            }
            default -> throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
        }
        roomRepository.save(room);
        eventService.append(new ClocktowerEventAppendRequest(room.getId(), ClocktowerEventType.PHASE_CHANGED,
                room.getPhase(), room.getCurrentDayNo(), room.getCurrentNightNo(),
                principal == null ? null : principal.userId(), null, null, ClocktowerVisibility.PUBLIC, List.of(),
                Map.of("phase", room.getPhase().name())));
        ensureNightTasks(room, principal);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse skipNightTask(Long roomId, Long taskId, SkipNightTaskRequest request,
                                                RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_SKIP_REASON_REQUIRED");
        }
        ClocktowerStorytellerTaskPo task = taskRepository.findById(taskId)
                .filter(candidate -> !candidate.isDeleted() && candidate.getRoomId().equals(roomId))
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_TASK_NOT_FOUND"));
        if (!isNight(room.getPhase()) || task.getNightNo() != room.getCurrentNightNo()) {
            throw new ClocktowerException("CLOCKTOWER_NIGHT_TASK_NOT_CURRENT");
        }
        task.setStatus(STATUS_SKIPPED);
        task.setNote(request.reason());
        taskRepository.save(task);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse closeNomination(Long roomId, Long nominationId, CloseNominationRequest request,
                                                  RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        if (room.getPhase() != ClocktowerPhase.NOMINATION) {
            throw new ClocktowerException("CLOCKTOWER_NOMINATION_PHASE_INVALID");
        }
        ClocktowerNominationPo nomination = nominationRepository.findByIdAndRoomIdAndDeletedFalse(nominationId, roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_FOUND"));
        if (!NOMINATION_OPEN.equals(nomination.getStatus())) {
            throw new ClocktowerException("CLOCKTOWER_NOMINATION_NOT_OPEN");
        }
        nomination.setStatus(NOMINATION_CLOSED);
        nominationRepository.save(nomination);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse confirmExecution(Long roomId, ExecutionConfirmRequest request,
                                                   RbacPrincipal principal) {
        ClocktowerRoomPo room = roomRepository.findLockedByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        ClocktowerAccess.requireStoryteller(room, principal);
        if (room.getPhase() != ClocktowerPhase.EXECUTION) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_PHASE_INVALID");
        }
        if (request == null || !StringUtils.hasText(request.note())) {
            throw new ClocktowerException("CLOCKTOWER_EXECUTION_NOTE_REQUIRED");
        }
        ClocktowerExecutionDeathPolicy deathPolicy = request.deathPolicy() == null
                ? ClocktowerExecutionDeathPolicy.NO_CHANGE
                : request.deathPolicy();
        ClocktowerFlowResponse flow = buildFlow(room);
        ExecutionCandidateResponse candidate = flow.executionCandidate();
        if (candidate.resolved()) {
            return flow;
        }
        if (Boolean.TRUE.equals(request.execute())) {
            if (!candidate.executable() || candidate.nominationId() == null || candidate.nomineeSeatId() == null) {
                throw new ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_REQUIRED");
            }
            if (deathPolicy == ClocktowerExecutionDeathPolicy.MARK_DEAD) {
                ClocktowerSeatPo nominee = seatRepository
                        .findByIdAndRoomIdAndDeletedFalse(candidate.nomineeSeatId(), roomId)
                        .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_SEAT_NOT_FOUND"));
                if ("DEAD".equals(nominee.getLifeStatus())) {
                    throw new ClocktowerException("CLOCKTOWER_EXECUTION_TARGET_ALREADY_DEAD");
                }
            }
            rulingService.create(roomId, new ClocktowerRulingCreateRequest(
                    ClocktowerRulingType.EXECUTE_PLAYER, candidate.nomineeSeatId(), candidate.nominationId(),
                    null, null, null, ClocktowerRulingReason.VOTE_EXECUTION, request.note(),
                    "一名玩家被处决", ClocktowerVisibility.PUBLIC, false), principal);
            if (deathPolicy == ClocktowerExecutionDeathPolicy.MARK_DEAD) {
                rulingService.create(roomId, new ClocktowerRulingCreateRequest(
                        ClocktowerRulingType.MARK_DEAD, candidate.nomineeSeatId(), null,
                        null, null, null, ClocktowerRulingReason.VOTE_EXECUTION, request.note(),
                        "一名玩家死亡", ClocktowerVisibility.PUBLIC, false), principal);
            }
        } else {
            if (deathPolicy == ClocktowerExecutionDeathPolicy.MARK_DEAD) {
                throw new ClocktowerException("CLOCKTOWER_EXECUTION_DEATH_POLICY_INVALID");
            }
            if (candidate.executable()) {
                throw new ClocktowerException("CLOCKTOWER_EXECUTION_CANDIDATE_EXISTS");
            }
            rulingService.create(roomId, new ClocktowerRulingCreateRequest(
                    ClocktowerRulingType.SKIP_EXECUTION, null, null, null,
                    null, null, ClocktowerRulingReason.VOTE_EXECUTION, request.note(),
                    "今日无人被处决", ClocktowerVisibility.PUBLIC, false), principal);
        }
        ClocktowerRoomPo refreshed = roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
        return buildFlow(refreshed);
    }

    private ClocktowerFlowResponse buildFlow(ClocktowerRoomPo room) {
        List<ClocktowerStorytellerTaskPo> nightTasks = isNight(room.getPhase())
                ? taskRepository.findByRoomIdAndNightNoAndDeletedFalseOrderBySortOrderAsc(
                room.getId(), room.getCurrentNightNo())
                : List.of();
        NightTaskSummaryResponse nightSummary = nightSummary(nightTasks);
        List<ClocktowerNominationPo> dayNominations = nominationRepository
                .findByRoomIdAndDayNoAndDeletedFalseOrderByIdAsc(room.getId(), room.getCurrentDayNo());
        NominationSummaryResponse openNomination = dayNominations.stream()
                .filter(nomination -> NOMINATION_OPEN.equals(nomination.getStatus()))
                .max(Comparator.comparing(ClocktowerNominationPo::getId))
                .map(this::nominationSummary)
                .orElse(null);
        List<ClocktowerSeatPo> seats = seatRepository.findByRoomIdAndDeletedFalseOrderBySeatNoAsc(room.getId());
        int aliveCount = (int) seats.stream().filter(seat -> "ALIVE".equals(seat.getLifeStatus())).count();
        ExecutionCandidateResponse execution = executionCandidate(room, dayNominations, aliveCount);
        ClocktowerFlowFact fact = new ClocktowerFlowFact(room.getPhase(), room.getCurrentDayNo(),
                room.getCurrentNightNo(), nightSummary.pending(), openNomination != null,
                execution.resolved(), demonAlive(seats), allDemonsDead(seats), aliveCount,
                execution.voteCount(), false, execution.executable());
        var decision = ruleEngine.evaluateFlow(fact);
        VictoryCandidateResponse victory = decision.victoryCandidate() == null ? null
                : new VictoryCandidateResponse(decision.victoryCandidate().winner(),
                decision.victoryCandidate().reason());
        return new ClocktowerFlowResponse(room.getId(), GamePhaseResponse.from(room), decision.nextTransition(),
                decision.advanceAllowed(), decision.blockingReasons(), nightSummary, openNomination, execution, victory);
    }

    private ExecutionCandidateResponse executionCandidate(ClocktowerRoomPo room,
                                                          List<ClocktowerNominationPo> nominations,
                                                          int aliveCount) {
        int threshold = (aliveCount + 1) / 2;
        List<ClocktowerNominationPo> closed = nominations.stream()
                .filter(nomination -> NOMINATION_CLOSED.equals(nomination.getStatus()))
                .toList();
        if (executionResolved(room, closed)) {
            return new ExecutionCandidateResponse(true, false, null, null, 0, threshold, "EXECUTION_CONFIRMED");
        }
        if (room.getPhase() != ClocktowerPhase.EXECUTION || closed.isEmpty()) {
            return new ExecutionCandidateResponse(false, false, null, null, 0, threshold, "NO_CLOSED_NOMINATION");
        }
        int topVote = closed.stream().mapToInt(ClocktowerNominationPo::getVoteCount).max().orElse(0);
        if (topVote < threshold) {
            return new ExecutionCandidateResponse(false, false, null, null, topVote, threshold, "BELOW_THRESHOLD");
        }
        List<ClocktowerNominationPo> top = closed.stream()
                .filter(nomination -> nomination.getVoteCount() == topVote)
                .toList();
        if (top.size() != 1) {
            return new ExecutionCandidateResponse(false, false, null, null, topVote, threshold, "TIED_TOP_VOTE");
        }
        ClocktowerNominationPo candidate = top.getFirst();
        return new ExecutionCandidateResponse(false, true, candidate.getId(), candidate.getNomineeSeatId(),
                candidate.getVoteCount(), threshold, "EXECUTION_CANDIDATE");
    }

    private boolean executionResolved(ClocktowerRoomPo room, List<ClocktowerNominationPo> closed) {
        return closed.stream().anyMatch(ClocktowerNominationPo::isExecuted)
                || eventRepository.existsByRoomIdAndDayNoAndEventTypeAndDeletedFalse(
                room.getId(), room.getCurrentDayNo(), ClocktowerEventType.EXECUTION_SKIPPED);
    }

    private void ensureNightTasks(ClocktowerRoomPo room, RbacPrincipal principal) {
        if (isNight(room.getPhase())) {
            grimoireService.getGrimoire(room.getId(), principal);
        }
    }

    private NightTaskSummaryResponse nightSummary(List<ClocktowerStorytellerTaskPo> tasks) {
        int pending = (int) tasks.stream().filter(task -> STATUS_PENDING.equals(task.getStatus())).count();
        int done = (int) tasks.stream().filter(task -> STATUS_DONE.equals(task.getStatus())).count();
        int skipped = (int) tasks.stream().filter(task -> STATUS_SKIPPED.equals(task.getStatus())).count();
        return new NightTaskSummaryResponse(tasks.size(), pending, done, skipped);
    }

    private NominationSummaryResponse nominationSummary(ClocktowerNominationPo nomination) {
        return new NominationSummaryResponse(nomination.getId(), nomination.getNominatorSeatId(),
                nomination.getNomineeSeatId(), nomination.getVoteCount(), nomination.getStatus());
    }

    private boolean demonAlive(List<ClocktowerSeatPo> seats) {
        return seats.stream().anyMatch(seat -> seat.getRoleType() == ClocktowerRoleType.DEMON
                && "ALIVE".equals(seat.getLifeStatus()));
    }

    private boolean allDemonsDead(List<ClocktowerSeatPo> seats) {
        return seats.stream().anyMatch(seat -> seat.getRoleType() == ClocktowerRoleType.DEMON)
                && seats.stream().filter(seat -> seat.getRoleType() == ClocktowerRoleType.DEMON)
                .noneMatch(seat -> "ALIVE".equals(seat.getLifeStatus()));
    }

    private static boolean isNight(ClocktowerPhase phase) {
        return phase == ClocktowerPhase.FIRST_NIGHT || phase == ClocktowerPhase.NIGHT;
    }

    private ClocktowerRoomPo room(Long roomId) {
        return roomRepository.findByIdAndDeletedFalse(roomId)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_ROOM_NOT_FOUND"));
    }
}
