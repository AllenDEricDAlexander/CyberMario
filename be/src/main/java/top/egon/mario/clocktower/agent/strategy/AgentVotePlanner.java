package top.egon.mario.clocktower.agent.strategy;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;

import java.util.Map;

@Component
public class AgentVotePlanner {

    public AgentDecision plan(AgentDecisionContext context) {
        AgentLegalIntentView yes = voteIntent(context, true);
        AgentLegalIntentView no = voteIntent(context, false);
        if (yes == null && no == null) {
            return new AgentDecision(new AgentIntent.Noop("vote is not legal"), "vote unavailable", Map.of());
        }
        AgentLegalIntentView reference = yes == null ? no : yes;
        Long nominee = AgentStrategySupport.longValue(reference.payload().get("nomineeGameSeatId"));
        int score = nominee == null ? 50 : AgentStrategySupport.memoryScore(context, nominee, "SUSPICION_SCORE");
        boolean dead = "DEAD".equals(context.view().lifeStatus());
        boolean voteYes = score >= (dead ? 80 : 65);
        if ("EVIL".equals(context.view().myAlignment())) {
            voteYes = score >= 55 && !AgentStrategySupport.evilDemonSeatIds(context).contains(nominee);
        }
        if (voteYes && yes != null) {
            return new AgentDecision(new AgentIntent.Vote(yes.nominationId(), true, "score " + score),
                    "selected yes vote", Map.of("nomineeGameSeatId", nominee, "score", score));
        }
        return new AgentDecision(new AgentIntent.Vote(reference.nominationId(), false, "score " + score),
                "selected no vote", Map.of("nomineeGameSeatId", nominee, "score", score));
    }

    private AgentLegalIntentView voteIntent(AgentDecisionContext context, boolean voteValue) {
        return context.legalIntents().stream()
                .filter(intent -> "VOTE".equals(intent.intentType()))
                .filter(intent -> intent.voteValue() != null && intent.voteValue() == voteValue)
                .findFirst()
                .orElse(null);
    }
}
