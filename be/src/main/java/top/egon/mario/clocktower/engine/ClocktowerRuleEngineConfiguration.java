package top.egon.mario.clocktower.engine;

import org.kie.api.KieBase;
import org.kie.internal.utils.KieHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClocktowerRuleEngineConfiguration {

    @Bean
    KieBase clocktowerBoardValidationKieBase() {
        return new KieHelper()
                .addFromClassPath("clocktower/rules/board/board-validation.drl")
                .build();
    }
}
