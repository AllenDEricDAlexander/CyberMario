package top.egon.mario.clocktower.engine.flow;

public record ExecutionCandidateDecision(
        boolean executable,
        String reason
) {
}
