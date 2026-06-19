package top.egon.mario.clocktower.engine.flow;

import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;

import java.util.ArrayList;
import java.util.List;

public class ClocktowerFlowDecisionCollector {

    private ClocktowerFlowTransition nextTransition = ClocktowerFlowTransition.NONE;
    private boolean advanceAllowed = true;
    private final List<String> blockingReasons = new ArrayList<>();
    private ExecutionCandidateDecision executionCandidate;
    private VictoryCandidateDecision victoryCandidate;

    public ClocktowerFlowTransition nextTransition() {
        return nextTransition;
    }

    public boolean advanceAllowed() {
        return advanceAllowed;
    }

    public List<String> blockingReasons() {
        return List.copyOf(blockingReasons);
    }

    public ExecutionCandidateDecision executionCandidate() {
        return executionCandidate;
    }

    public VictoryCandidateDecision victoryCandidate() {
        return victoryCandidate;
    }

    public void allow(ClocktowerFlowTransition transition) {
        this.nextTransition = transition;
        this.advanceAllowed = true;
    }

    public void block(ClocktowerFlowTransition transition, String reason) {
        this.nextTransition = transition;
        this.advanceAllowed = false;
        if (!blockingReasons.contains(reason)) {
            blockingReasons.add(reason);
        }
    }

    public void executionCandidate(ExecutionCandidateDecision decision) {
        this.executionCandidate = decision;
    }

    public void victoryCandidate(VictoryCandidateDecision decision) {
        this.victoryCandidate = decision;
    }
}
