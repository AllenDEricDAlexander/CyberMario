package top.egon.mario.rbac.activation.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.activation.delivery.ActivationDeliveryMode;
import top.egon.mario.rbac.service.RbacException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
    void reservesTheTwentiethAttemptAndRejectsTheTwentyFirstRequest() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        assertThatCode(() -> limiter.beginAttempt("127.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.beginAttempt("127.0.0.1"))
                .extracting("code")
                .isEqualTo("AUTH_ACTIVATION_RATE_LIMITED");
    }

    @Test
    void releasesSuccessfulOrNonCountedAttempts() {
        ActivationFailureRateLimiter limiter = limiter(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        ActivationFailureRateLimiter.AttemptReservation reservation =
                limiter.beginAttempt("127.0.0.1");
        limiter.release(reservation);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(),
                anyString(), anyString(), anyString(), eq("20"), eq("600000"));
        verify(redisTemplate, times(2))
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void parallelThresholdAdmissionAllowsOnlyOneRequest() throws Exception {
        ActivationFailureRateLimiter limiter = limiter(true);
        AtomicInteger rollingCount = new AtomicInteger(19);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> rollingCount.incrementAndGet() <= 20 ? 1L : 0L);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> begin(limiter, start));
            Future<Object> second = executor.submit(() -> begin(limiter, start));
            start.countDown();
            List<Object> outcomes = List.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(ActivationFailureRateLimiter.AttemptReservation.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(RbacException.class::isInstance)
                    .singleElement()
                    .extracting(value -> ((RbacException) value).getCode())
                    .isEqualTo("AUTH_ACTIVATION_RATE_LIMITED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledLimiterDoesNotCallRedis() {
        ActivationFailureRateLimiter limiter = limiter(false);

        ActivationFailureRateLimiter.AttemptReservation reservation =
                limiter.beginAttempt("127.0.0.1");
        limiter.release(reservation);

        verifyNoInteractions(redisTemplate);
    }

    private Object begin(ActivationFailureRateLimiter limiter, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            return limiter.beginAttempt("127.0.0.1");
        } catch (RbacException exception) {
            return exception;
        }
    }

    private ActivationFailureRateLimiter limiter(boolean enabled) {
        RbacOttProperties properties = new RbacOttProperties(true, Duration.ofHours(24),
                URI.create("http://localhost:5173"), ActivationDeliveryMode.MOCK, true, enabled);
        return new ActivationFailureRateLimiter(redisTemplate, properties, clock);
    }
}
