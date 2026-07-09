package top.egon.mario.clocktower.agent.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.agent.strategy.troublebrewing.TroubleBrewingBluffPlanner;
import top.egon.mario.clocktower.agent.view.dto.AgentPrivateInfoView;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentSpeechPlanner {

    private final TroubleBrewingBluffPlanner bluffPlanner;

    public AgentDecision planMicTurn(AgentDecisionContext context) {
        if (!AgentStrategySupport.hasIntent(context, "PUBLIC_SPEECH")) {
            return passOrNoop(context, "cannot speak");
        }
        if ("EVIL".equals(context.view().myAlignment())) {
            Map<String, Object> bluff = bluffPlanner.loadOrCreatePlan(context);
            return new AgentDecision(new AgentIntent.PublicSpeech(evilSpeech(bluff)),
                    "evil bluff speech", Map.of("alignment", "EVIL", "claimRoleCode", bluff.get("claimRoleCode")));
        }
        if (!context.view().privateInfos().isEmpty()) {
            AgentPrivateInfoView info = context.view().privateInfos().getLast();
            return new AgentDecision(new AgentIntent.PublicSpeech(goodInfoSpeech(info)),
                    "shared private info and released mic", Map.of("privateInfoEventId", info.eventId()));
        }
        if (context.profile().getTalkativeness() < 55) {
            return passOrNoop(context, "low talkativeness");
        }
        Long target = context.view().publicSeats().stream()
                .filter(seat -> !seat.gameSeatId().equals(context.view().myGameSeatId()))
                .filter(seat -> "ACTIVE".equals(seat.status()))
                .map(seat -> seat.gameSeatId())
                .findFirst()
                .orElse(null);
        if (target == null) {
            return passOrNoop(context, "no useful speech target");
        }
        return new AgentDecision(new AgentIntent.PublicSpeech(
                "我这轮先听一下" + target + "号的说法，尤其是白天投票和提名理由。"),
                "asked another seat for explanation", Map.of("questionTargetGameSeatId", target));
    }

    public AgentDecision planGrabMic(AgentDecisionContext context) {
        if (!AgentStrategySupport.hasIntent(context, "GRAB_MIC")) {
            return new AgentDecision(new AgentIntent.Noop("grab mic is not legal"),
                    "grab mic unavailable", Map.of("triggerType", context.triggerType()));
        }
        if ("EVIL".equals(context.view().myAlignment()) && context.profile().getTalkativeness() >= 65) {
            return new AgentDecision(new AgentIntent.GrabMic("补充一下，我的视角暂时先按前面说法推进。"),
                    "grab mic for bluff reinforcement", Map.of("alignment", "EVIL"));
        }
        if (!context.view().privateInfos().isEmpty() && context.profile().getTalkativeness() >= 70) {
            return new AgentDecision(new AgentIntent.GrabMic("我这边有新信息，先补一句再让大家继续。"),
                    "grab mic for fresh private info", Map.of("privateInfoCount", context.view().privateInfos().size()));
        }
        return new AgentDecision(new AgentIntent.Noop("no high priority grab reason"),
                "no grab reason", Map.of("talkativeness", context.profile().getTalkativeness()));
    }

    private AgentDecision passOrNoop(AgentDecisionContext context, String reason) {
        if (AgentStrategySupport.hasIntent(context, "PASS")) {
            return new AgentDecision(new AgentIntent.Pass(reason), reason, Map.of("passType", "MIC_TURN"));
        }
        return new AgentDecision(new AgentIntent.Noop(reason), reason, Map.of());
    }

    private String goodInfoSpeech(AgentPrivateInfoView info) {
        String role = info.roleCode() == null ? "身份信息" : info.roleCode();
        return "我这边有信息，偏向先按" + role + "的视角观察，暂时不把细节说死，想继续听相邻位置的发言。";
    }

    private String evilSpeech(Map<String, Object> bluff) {
        Object claim = bluff.getOrDefault("claimRoleCode", "CHEF");
        Object fakeInfo = bluff.getOrDefault("fakeInfo", Map.of("chefNumber", 1));
        return "我先报一个" + claim + "视角，信息是" + fakeInfo + "，这轮我更想听被推位置的解释。";
    }
}
