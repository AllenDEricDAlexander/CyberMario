package top.egon.mario.agent.externalim.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "mario.agent.external-im.worker")
public record ExternalChatWorkerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int batchSize,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT1S") Duration initialDelay,
        @DefaultValue("PT1S") Duration pollInterval,
        @DefaultValue("PT5S") Duration retryDelay,
        @DefaultValue("PT2M") Duration staleAfter
) {

    @ConstructorBinding
    public ExternalChatWorkerProperties {
        batchSize = Math.max(1, Math.min(batchSize, 100));
        maxAttempts = Math.max(1, Math.min(maxAttempts, 10));
        initialDelay = nonNegative(initialDelay, Duration.ofSeconds(1));
        pollInterval = positive(pollInterval, Duration.ofSeconds(1));
        retryDelay = positive(retryDelay, Duration.ofSeconds(5));
        staleAfter = positive(staleAfter, Duration.ofMinutes(2));
    }

    private static Duration nonNegative(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
