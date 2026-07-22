package top.egon.mario.agent.externalim.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.enums.ModelProviderType;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "mario.agent.external-im.guard")
public record ChatGuardProperties(
        @DefaultValue("DASHSCOPE") ModelProviderType provider,
        @DefaultValue("qwen3.7-plus") String model,
        @DefaultValue("0") BigDecimal temperature,
        @DefaultValue("256") Integer maxTokens,
        @DefaultValue("0.85") BigDecimal replyThreshold,
        @DefaultValue("PT5S") Duration timeout
) {

    @ConstructorBinding
    public ChatGuardProperties {
        provider = provider == null ? ModelProviderType.DASHSCOPE : provider;
        model = StringUtils.hasText(model) ? model.trim() : "qwen3.7-plus";
        temperature = temperature == null ? BigDecimal.ZERO : temperature;
        maxTokens = maxTokens == null || maxTokens <= 0 ? 256 : maxTokens;
        replyThreshold = replyThreshold == null ? new BigDecimal("0.85") : replyThreshold;
        if (replyThreshold.compareTo(BigDecimal.ZERO) < 0
                || replyThreshold.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("guard reply threshold must be between 0 and 1");
        }
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(5) : timeout;
    }
}
