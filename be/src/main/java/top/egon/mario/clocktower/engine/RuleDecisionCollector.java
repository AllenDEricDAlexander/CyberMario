package top.egon.mario.clocktower.engine;

import java.util.ArrayList;
import java.util.List;

public class RuleDecisionCollector {

    private final List<RuleViolationDecision> violations = new ArrayList<>();
    private final List<ScoreDecision> scores = new ArrayList<>();

    public void reject(String code, String message, String severity) {
        violations.add(new RuleViolationDecision(code, message, severity));
    }

    public void score(String scoreType, int delta, String reason) {
        scores.add(new ScoreDecision(scoreType, delta, reason));
    }

    public List<RuleViolationDecision> violations() {
        return List.copyOf(violations);
    }

    public List<ScoreDecision> scores() {
        return List.copyOf(scores);
    }
}
