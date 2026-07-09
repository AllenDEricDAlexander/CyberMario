package top.egon.mario.clocktower.agent.strategy;

import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.view.dto.AgentLegalIntentView;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class AgentNightChoicePlanner {

    public AgentDecision plan(AgentDecisionContext context) {
        AgentLegalIntentView intent = AgentStrategySupport.firstIntent(context, "NIGHT_CHOICE");
        if (intent == null) {
            return new AgentDecision(new AgentIntent.Noop("night choice is not legal"),
                    "night choice unavailable", Map.of());
        }
        List<Long> legalTargets = AgentStrategySupport.longList(intent.payload().get("legalTargetGameSeatIds"));
        String roleCode = AgentStrategySupport.stringValue(
                intent.payload().getOrDefault("roleCode", context.view().myRoleCode()));
        String taskType = AgentStrategySupport.stringValue(intent.payload().get("taskType"));
        if ("RECEIVE_INFO".equals(taskType) && legalTargets.isEmpty()) {
            return new AgentDecision(new AgentIntent.NightChoice(intent.taskId(), List.of(),
                    Map.of("taskId", intent.taskId())), "receive info task has no target",
                    Map.of("roleCode", roleCode));
        }
        Long target = chooseNightTarget(context, roleCode, legalTargets);
        if (target == null) {
            return new AgentDecision(new AgentIntent.Noop("no legal night target"),
                    "no legal night target", Map.of("roleCode", roleCode));
        }
        return new AgentDecision(new AgentIntent.NightChoice(intent.taskId(), List.of(target),
                Map.of("taskId", intent.taskId())), "selected night target",
                Map.of("roleCode", roleCode, "targetGameSeatId", target));
    }

    private Long chooseNightTarget(AgentDecisionContext context, String roleCode, List<Long> legalTargets) {
        if (legalTargets.isEmpty()) {
            return null;
        }
        return switch (roleCode) {
            case "MONK" -> legalTargets.stream()
                    .filter(target -> !target.equals(context.view().myGameSeatId()))
                    .max(Comparator.comparingInt(target -> AgentStrategySupport.memoryScore(context, target,
                            "TRUST_SCORE")))
                    .orElse(legalTargets.getFirst());
            case "IMP", "POISONER" -> legalTargets.stream()
                    .filter(target -> !AgentStrategySupport.evilTeamSeatIds(context).contains(target))
                    .findFirst()
                    .orElse(legalTargets.getFirst());
            case "FORTUNETELLER" -> legalTargets.stream()
                    .max(Comparator.comparingInt(target -> AgentStrategySupport.memoryScore(context, target,
                            "SUSPICION_SCORE")))
                    .orElse(legalTargets.getFirst());
            default -> legalTargets.getFirst();
        };
    }
}
