package top.egon.mario.clocktower.engine;

import lombok.RequiredArgsConstructor;
import org.kie.api.KieBase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClocktowerRuleEngine {

    private final KieBase clocktowerBoardValidationKieBase;

    public RuleDecisionCollector validateBoard(BoardCandidateFact fact) {
        RuleDecisionCollector collector = new RuleDecisionCollector();
        try (var session = clocktowerBoardValidationKieBase.newKieSession()) {
            session.insert(fact);
            session.insert(collector);
            session.fireAllRules();
        }
        return collector;
    }
}
