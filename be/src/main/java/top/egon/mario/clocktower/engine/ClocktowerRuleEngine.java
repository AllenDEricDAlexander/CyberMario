package top.egon.mario.clocktower.engine;

import org.kie.api.KieBase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowDecisionCollector;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;

@Component
public class ClocktowerRuleEngine {

    private final KieBase clocktowerBoardValidationKieBase;
    private final KieBase clocktowerFlowKieBase;

    public ClocktowerRuleEngine(@Qualifier("clocktowerBoardValidationKieBase") KieBase clocktowerBoardValidationKieBase,
                                @Qualifier("clocktowerFlowKieBase") KieBase clocktowerFlowKieBase) {
        this.clocktowerBoardValidationKieBase = clocktowerBoardValidationKieBase;
        this.clocktowerFlowKieBase = clocktowerFlowKieBase;
    }

    public RuleDecisionCollector validateBoard(BoardCandidateFact fact) {
        RuleDecisionCollector collector = new RuleDecisionCollector();
        try (var session = clocktowerBoardValidationKieBase.newKieSession()) {
            session.insert(fact);
            session.insert(collector);
            session.fireAllRules();
        }
        return collector;
    }

    public ClocktowerFlowDecisionCollector evaluateFlow(ClocktowerFlowFact fact) {
        ClocktowerFlowDecisionCollector collector = new ClocktowerFlowDecisionCollector();
        try (var session = clocktowerFlowKieBase.newKieSession()) {
            session.insert(fact);
            session.insert(collector);
            session.fireAllRules();
        }
        return collector;
    }
}
