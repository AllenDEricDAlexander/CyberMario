package top.egon.mario.agent.soul.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSoulPropertiesTests {

    @Test
    void bindsEvolutionProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "mario.agent.soul.evolution-enabled", "false",
                "mario.agent.soul.evolution-provider", "DASHSCOPE",
                "mario.agent.soul.evolution-model", "qwen-soul-test",
                "mario.agent.soul.evolution-temperature", "0.35",
                "mario.agent.soul.evolution-max-tokens", "2048"
        )));

        AgentSoulProperties properties = binder
                .bind("mario.agent.soul", AgentSoulProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.evolutionEnabled()).isFalse();
        assertThat(properties.evolutionProvider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(properties.evolutionModel()).isEqualTo("qwen-soul-test");
        assertThat(properties.evolutionTemperature()).isEqualByComparingTo(new BigDecimal("0.35"));
        assertThat(properties.evolutionMaxTokens()).isEqualTo(2048);
    }

    @Test
    void usesDefaultsWhenPropertiesAreMissing() {
        AgentSoulProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("mario.agent.soul", AgentSoulProperties.class);

        assertThat(properties.evolutionEnabled()).isTrue();
        assertThat(properties.evolutionProvider()).isEqualTo(ModelProviderType.DASHSCOPE);
        assertThat(properties.evolutionModel()).isEqualTo("qwen3.6-plus-2026-04-02");
        assertThat(properties.evolutionTemperature()).isEqualByComparingTo(new BigDecimal("0.2"));
        assertThat(properties.evolutionMaxTokens()).isEqualTo(4096);
    }
}
