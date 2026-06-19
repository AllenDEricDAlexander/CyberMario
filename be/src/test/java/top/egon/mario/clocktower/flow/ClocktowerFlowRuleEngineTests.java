package top.egon.mario.clocktower.flow;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngine;
import top.egon.mario.clocktower.engine.ClocktowerRuleEngineConfiguration;
import top.egon.mario.clocktower.engine.flow.ClocktowerFlowFact;
import top.egon.mario.clocktower.flow.dto.ClocktowerFlowTransition;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerFlowRuleEngineTests {

    private final ClocktowerRuleEngine engine = new ClocktowerRuleEngine(
            new ClocktowerRuleEngineConfiguration().clocktowerBoardValidationKieBase(new DefaultResourceLoader()),
            new ClocktowerRuleEngineConfiguration().clocktowerFlowKieBase(new DefaultResourceLoader()));

    @Test
    void firstNightIsBlockedByPendingNightTasks() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.FIRST_NIGHT, 0, 1,
                2, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.COMPLETE_FIRST_NIGHT);
        assertThat(collector.advanceAllowed()).isFalse();
        assertThat(collector.blockingReasons()).containsExactly("CLOCKTOWER_NIGHT_TASKS_PENDING");
    }

    @Test
    void dayCanStartNomination() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.DAY, 1, 1,
                0, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.START_NOMINATION);
        assertThat(collector.advanceAllowed()).isTrue();
        assertThat(collector.blockingReasons()).isEmpty();
    }

    @Test
    void executionCannotAdvanceBeforeResolution() {
        ClocktowerFlowFact fact = new ClocktowerFlowFact(ClocktowerPhase.EXECUTION, 1, 1,
                0, false, false, false, false, 5, 0, false, false);

        var collector = engine.evaluateFlow(fact);

        assertThat(collector.nextTransition()).isEqualTo(ClocktowerFlowTransition.START_NIGHT);
        assertThat(collector.advanceAllowed()).isFalse();
        assertThat(collector.blockingReasons()).containsExactly("CLOCKTOWER_EXECUTION_NOT_RESOLVED");
    }
}
