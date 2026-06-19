package top.egon.mario.clocktower.engine;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.internal.utils.KieHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Configuration
public class ClocktowerRuleEngineConfiguration {

    private static final String BOARD_VALIDATION_RULE = "clocktower/rules/board/board-validation.drl";
    private static final String FLOW_TRANSITION_RULE = "clocktower/rules/flow/flow-transition.drl";

    @Bean
    public KieBase clocktowerBoardValidationKieBase(ResourceLoader resourceLoader) {
        return new KieHelper()
                .addResource(loadDrlResource(resourceLoader, BOARD_VALIDATION_RULE), ResourceType.DRL)
                .build();
    }

    @Bean
    public KieBase clocktowerFlowKieBase(ResourceLoader resourceLoader) {
        return new KieHelper()
                .addResource(loadDrlResource(resourceLoader, FLOW_TRANSITION_RULE), ResourceType.DRL)
                .build();
    }

    private static Resource loadDrlResource(ResourceLoader resourceLoader, String path) {
        try {
            Resource resource = KieServices.Factory.get().getResources()
                    .newInputStreamResource(resourceLoader.getResource("classpath:" + path).getInputStream());
            resource.setSourcePath("src/main/resources/" + path);
            return resource;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Clocktower rule resource: " + path, e);
        }
    }
}
