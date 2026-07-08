package top.egon.mario.clocktower.game.mic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.mario.clocktower.common.ClocktowerException;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "clocktower.public-mic")
public class ClocktowerPublicMicProperties {

    private long roundRobinTurnSeconds = 45;

    private long grabMicTotalSeconds = 300;

    private long grabMicHoldSeconds = 45;

    public Duration roundRobinTurnDuration() {
        return Duration.ofSeconds(requirePositive(roundRobinTurnSeconds, "CLOCKTOWER_MIC_ROUND_SECONDS_INVALID"));
    }

    public Duration grabMicTotalDuration() {
        return Duration.ofSeconds(requirePositive(grabMicTotalSeconds, "CLOCKTOWER_MIC_GRAB_TOTAL_SECONDS_INVALID"));
    }

    public Duration grabMicHoldDuration() {
        return Duration.ofSeconds(requirePositive(grabMicHoldSeconds, "CLOCKTOWER_MIC_GRAB_HOLD_SECONDS_INVALID"));
    }

    private long requirePositive(long seconds, String code) {
        if (seconds <= 0) {
            throw new ClocktowerException(code);
        }
        return seconds;
    }
}
