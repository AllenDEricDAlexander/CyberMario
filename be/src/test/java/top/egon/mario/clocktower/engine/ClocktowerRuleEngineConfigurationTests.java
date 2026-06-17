package top.egon.mario.clocktower.engine;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerRuleEngineConfigurationTests {

    @Test
    void loadsBoardValidationRulesFromClasspathResource() {
        ClocktowerRuleEngineConfiguration configuration = new ClocktowerRuleEngineConfiguration();
        ClocktowerRuleEngine engine = new ClocktowerRuleEngine(
                configuration.clocktowerBoardValidationKieBase(new DefaultResourceLoader()));

        RuleDecisionCollector collector = engine.validateBoard(new BoardCandidateFact(
                ClocktowerScriptCode.TROUBLE_BREWING, 4, List.of("EMPATH", "IMP"), 1, 0, 0, 1));

        assertThat(collector.violations())
                .extracting(RuleViolationDecision::code)
                .contains("BOARD_PLAYER_COUNT_TOO_LOW", "BOARD_ROLE_COUNT_MISMATCH");
    }
}
