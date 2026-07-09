package top.egon.mario.clocktower.agent.runtime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clocktower.agent")
public class ClocktowerAgentWorkerProperties {

    private long defaultResponseDelayMs = 800;

    private Worker worker = new Worker();

    public Duration defaultResponseDelay() {
        return Duration.ofMillis(Math.max(0L, defaultResponseDelayMs));
    }

    @Getter
    @Setter
    public static class Worker {

        private int batchSize = 10;

        private int maxAttempts = 3;

        private long initialDelayMs = 1000;

        private long fixedDelayMs = 1000;

        private long retryDelayMs = 1000;

        public int batchSize() {
            return Math.max(1, batchSize);
        }

        public int maxAttempts() {
            return Math.max(1, maxAttempts);
        }

        public long initialDelayMs() {
            return Math.max(0L, initialDelayMs);
        }

        public long fixedDelayMs() {
            return Math.max(1L, fixedDelayMs);
        }

        public Duration retryDelay() {
            return Duration.ofMillis(Math.max(0L, retryDelayMs));
        }
    }
}
