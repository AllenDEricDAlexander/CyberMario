package top.egon.mario.rbac.activation.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies activation failures use a rolling Redis window without exposing raw client addresses.
 */
class ActivationFailureRateLimiterTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsNineteenFailuresAndRejectsTheTwentyFirstRequest() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(19L, 20L);

        assertThatCode(() -> limiter.assertAllowed("127.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.assertAllowed("127.0.0.1"))
                .extracting("code")
                .isEqualTo("AUTH_ACTIVATION_RATE_LIMITED");
    }

    @Test
    void recordsFailureWithTheTenMinuteRollingWindowExpiry() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        limiter.recordFailure("127.0.0.1");

        verify(redisTemplate).execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString(), eq("600000"));
    }

    @Test
    void disabledLimiterDoesNotCallRedis() {
        ActivationFailureRateLimiter limiter = limiter(false);

        limiter.assertAllowed("127.0.0.1");
        limiter.recordFailure("127.0.0.1");

        verifyNoInteractions(redisTemplate);
    }

    private ActivationFailureRateLimiter limiter(boolean enabled) {
        RbacOttProperties properties = new RbacOttProperties(true, Duration.ofHours(24),
                URI.create("http://localhost:5173"), ActivationDeliveryMode.MOCK, true, enabled);
        return new ActivationFailureRateLimiter(redisTemplate, properties, clock);
    }
}
