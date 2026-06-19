package top.egon.mario.clocktower.flow.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.ClocktowerAccess;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.flow.ClocktowerFlowService;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionCandidateResponse;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.NightTaskSummaryResponse;
import top.egon.mario.clocktower.flow.dto.NominationSummaryResponse;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.clocktower.flow.dto.VictoryCandidateResponse;
import top.egon.mario.clocktower.grimoire.dto.response.GamePhaseResponse;
import top.egon.mario.clocktower.grimoire.po.ClocktowerNominationPo;
import top.egon.mario.clocktower.grimoire.po.ClocktowerStorytellerTaskPo;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerNominationRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerStorytellerTaskRepository;
import top.egon.mario.clocktower.grimoire.repository.ClocktowerVoteRepository;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomRepository;
import top.egon.mario.clocktower.room.repository.ClocktowerSeatRepository;
import top.egon.mario.clocktower.script.repository.ClocktowerRoleRepository;
import top.egon.mario.clocktower.event.service.ClocktowerEventService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Comparator;
import java.util.List;

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
    private final ClocktowerRuleEngine ruleEngine;

    @Override
    @Transactional(readOnly = true)
    public ClocktowerFlowResponse getFlow(Long roomId, RbacPrincipal principal) {
        ClocktowerRoomPo room = room(roomId);
        ClocktowerAccess.requireStoryteller(room, principal);
        return buildFlow(room);
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse advance(Long roomId, RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse skipNightTask(Long roomId, Long taskId, SkipNightTaskRequest request,
                                                RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse closeNomination(Long roomId, Long nominationId, CloseNominationRequest request,
                                                  RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
    }

    @Override
    @Transactional
    public ClocktowerFlowResponse confirmExecution(Long roomId, ExecutionConfirmRequest request,
                                                   RbacPrincipal principal) {
        throw new ClocktowerException("CLOCKTOWER_FLOW_TRANSITION_UNSUPPORTED");
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
        return closed.stream().anyMatch(ClocktowerNominationPo::isExecuted);
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
