package top.egon.mario.clocktower.agent.strategy;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class AgentNominationPlanner {

    public AgentDecision plan(AgentDecisionContext context) {
        if (!"ALIVE".equals(context.view().lifeStatus())) {
            return new AgentDecision(new AgentIntent.Noop("dead agents cannot nominate"),
                    "dead agent cannot nominate", Map.of());
        }
        AgentLegalIntentView intent = AgentStrategySupport.firstIntent(context, "NOMINATE");
        if (intent == null) {
            return new AgentDecision(new AgentIntent.Noop("nomination is not legal"),
                    "nomination unavailable", Map.of());
        }
        List<Long> targets = AgentStrategySupport.longList(intent.payload().get("eligibleTargetGameSeatIds"));
        Long chosen = targets.stream()
                .filter(target -> !AgentStrategySupport.evilDemonSeatIds(context).contains(target))
                .max(Comparator.comparingInt(target -> nominationScore(context, target)))
                .orElse(null);
        if (chosen == null) {
            return new AgentDecision(new AgentIntent.Noop("no safe nomination target"),
                    "no nomination target", Map.of("eligibleTargetGameSeatIds", targets));
        }
        int score = nominationScore(context, chosen);
        int threshold = "EVIL".equals(context.view().myAlignment()) ? 55 : 65;
        if (score < threshold) {
            return new AgentDecision(new AgentIntent.Noop("nomination score below threshold"),
                    "nomination score below threshold", Map.of("score", score, "threshold", threshold));
        }
        return new AgentDecision(new AgentIntent.Nominate(chosen, "score " + score),
                "selected nomination target", Map.of("targetGameSeatId", chosen, "score", score));
    }

    private int nominationScore(AgentDecisionContext context, Long targetGameSeatId) {
        return AgentStrategySupport.memoryScore(context, targetGameSeatId, "SUSPICION_SCORE");
    }
}
