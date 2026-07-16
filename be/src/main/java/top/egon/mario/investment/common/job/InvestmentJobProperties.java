package top.egon.mario.investment.common.job;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Operational limits for the durable Investment job worker.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mario.investment.job")
public class InvestmentJobProperties {

    private int batchSize = 10;

    private Duration leaseDuration = Duration.ofSeconds(30);

    private Duration heartbeatInterval = Duration.ofSeconds(10);

    private Duration retryBaseDelay = Duration.ofSeconds(1);

    private Duration retryMaxDelay = Duration.ofMinutes(5);

    private Runner runner = new Runner();

    public int batchSize() {
        return Math.max(1, batchSize);
    }

    public Duration leaseDuration() {
        return positive(leaseDuration, Duration.ofSeconds(30));
    }

    public Duration heartbeatInterval() {
        Duration configured = positive(heartbeatInterval, Duration.ofSeconds(10));
        Duration maximum = leaseDuration().dividedBy(2);
        return configured.compareTo(maximum) > 0 ? maximum : configured;
    }

    public Duration retryBaseDelay() {
        return nonNegative(retryBaseDelay, Duration.ofSeconds(1));
    }

    public Duration retryMaxDelay() {
        Duration configured = nonNegative(retryMaxDelay, Duration.ofMinutes(5));
        return configured.compareTo(retryBaseDelay()) < 0 ? retryBaseDelay() : configured;
    }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private Duration nonNegative(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }

    @Getter
    @Setter
    public static class Runner {

        private boolean enabled = true;

        private Duration initialDelay = Duration.ofSeconds(1);

        private Duration pollInterval = Duration.ofSeconds(1);

        public Duration initialDelay() {
            return initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
        }

        public Duration pollInterval() {
            return pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
                    ? Duration.ofSeconds(1) : pollInterval;
        }
    }
}
