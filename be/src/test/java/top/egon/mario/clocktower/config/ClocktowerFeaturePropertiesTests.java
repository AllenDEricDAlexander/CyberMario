package top.egon.mario.clocktower.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.mario.clocktower.game.mic.config.ClocktowerPublicMicProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ClocktowerFeaturePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void featureFlagsDefaultToSafeTaskSixteenValues() {
        contextRunner.run(context -> {
            ClocktowerFeatureProperties flags = context.getBean(ClocktowerFeatureProperties.class);
            ClocktowerPublicMicProperties mic = context.getBean(ClocktowerPublicMicProperties.class);

            assertThat(flags.agentPlayer().enabled()).isTrue();
            assertThat(flags.gameActions().enabled()).isTrue();
            assertThat(flags.newFlow().enabled()).isTrue();
            assertThat(flags.llmAgent().enabled()).isFalse();
            assertThat(mic.isEnabled()).isTrue();
        });
    }

    @Test
    void featureFlagsBindFromClocktowerProperties() {
        contextRunner
                .withPropertyValues(
                        "clocktower.agent-player.enabled=false",
                        "clocktower.game-actions.enabled=false",
                        "clocktower.public-mic.enabled=false",
                        "clocktower.new-flow.enabled=false",
                        "clocktower.llm-agent.enabled=true")
                .run(context -> {
                    ClocktowerFeatureProperties flags = context.getBean(ClocktowerFeatureProperties.class);
                    ClocktowerPublicMicProperties mic = context.getBean(ClocktowerPublicMicProperties.class);

                    assertThat(flags.agentPlayer().enabled()).isFalse();
                    assertThat(flags.gameActions().enabled()).isFalse();
                    assertThat(flags.newFlow().enabled()).isFalse();
                    assertThat(flags.llmAgent().enabled()).isTrue();
                    assertThat(mic.isEnabled()).isFalse();
                });
    }

    @EnableConfigurationProperties({ClocktowerFeatureProperties.class, ClocktowerPublicMicProperties.class})
    static class TestConfiguration {
    }
}
