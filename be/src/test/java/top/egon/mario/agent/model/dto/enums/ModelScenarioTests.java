package top.egon.mario.agent.model.dto.enums;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.model.service.model.ModelCallContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Investment audit attribution without changing existing scenario mappings.
 */
class ModelScenarioTests {

    @Test
    void investmentAgentScenarioIsAvailableForModelAuditContext() {
        ModelCallContext context = new ModelCallContext(1L, "trace-1", null, "thread-1",
                ModelScenario.INVESTMENT_AGENT, "request-1", null, null);

        assertThat(context.scenario()).isEqualTo(ModelScenario.INVESTMENT_AGENT);
        assertThat(ModelScenario.valueOf("INVESTMENT_AGENT")).isEqualTo(ModelScenario.INVESTMENT_AGENT);
    }

    @Test
    void existingScenarioNamesAndOrdinalsRemainStable() {
        assertThat(ModelScenario.UNKNOWN.ordinal()).isZero();
        assertThat(ModelScenario.AGENT_CHAT.ordinal()).isEqualTo(1);
        assertThat(ModelScenario.RAG_CHAT.ordinal()).isEqualTo(2);
        assertThat(ModelScenario.RAG_SUMMARY.ordinal()).isEqualTo(3);
        assertThat(ModelScenario.AGENT_SOUL_EVOLUTION.ordinal()).isEqualTo(4);
        assertThat(ModelScenario.BACKGROUND_TASK.ordinal()).isEqualTo(5);
    }
}
