package top.egon.mario.agent.soul.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;

import java.math.BigDecimal;

/**
 * Externalized settings for Agent SoulMD behavior.
 */
@ConfigurationProperties(prefix = "mario.agent.soul")
public record AgentSoulProperties(
        @DefaultValue("true")
        boolean evolutionEnabled,
        @DefaultValue("DASHSCOPE")
        ModelProviderType evolutionProvider,
        @DefaultValue("qwen3.6-plus-2026-04-02")
        String evolutionModel,
        @DefaultValue("0.2")
        BigDecimal evolutionTemperature,
        @DefaultValue("4096")
        Integer evolutionMaxTokens
) {

    private static final String DEFAULT_EVOLUTION_MODEL = "qwen3.6-plus-2026-04-02";

    public AgentSoulProperties() {
        this(true, ModelProviderType.DASHSCOPE, DEFAULT_EVOLUTION_MODEL, new BigDecimal("0.2"), 4096);
    }

    @ConstructorBinding
    public AgentSoulProperties {
        evolutionProvider = evolutionProvider == null ? ModelProviderType.DASHSCOPE : evolutionProvider;
        evolutionModel = StringUtils.hasText(evolutionModel) ? evolutionModel.trim() : DEFAULT_EVOLUTION_MODEL;
        evolutionTemperature = evolutionTemperature == null ? new BigDecimal("0.2") : evolutionTemperature;
        evolutionMaxTokens = evolutionMaxTokens == null || evolutionMaxTokens <= 0 ? 4096 : evolutionMaxTokens;
    }
}
