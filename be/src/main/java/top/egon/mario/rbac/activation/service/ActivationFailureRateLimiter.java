package top.egon.mario.rbac.activation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import top.egon.mario.rbac.activation.config.RbacOttProperties;
import top.egon.mario.rbac.service.RbacException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Enforces a per-address rolling failure window for public account activation attempts.
 */
@Component
@RequiredArgsConstructor
public class ActivationFailureRateLimiter {

    private static final String KEY_PREFIX = "rbac:activation:failures:";
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long FAILURE_LIMIT = 20L;

    private static final DefaultRedisScript<Long> BEGIN_ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[4]) then return 0 end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            if redis.call('ZCARD', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RbacOttProperties properties;
    private final Clock clock;

    public AttemptReservation beginAttempt(String ip) {
        if (!properties.rateLimitEnabled()) {
            return AttemptReservation.notTracked();
        }
        long now = clock.millis();
        String key = key(ip);
        String member = now + ":" + UUID.randomUUID();
        Long admitted = redisTemplate.execute(BEGIN_ATTEMPT_SCRIPT, List.of(key),
                String.valueOf(now - WINDOW.toMillis()),
                String.valueOf(now), member, String.valueOf(FAILURE_LIMIT),
                String.valueOf(WINDOW.toMillis()));
        if (Long.valueOf(0L).equals(admitted)) {
            throw new RbacException("AUTH_ACTIVATION_RATE_LIMITED",
                    "too many activation failures; try again later");
        }
        if (!Long.valueOf(1L).equals(admitted)) {
            throw new IllegalStateException("activation rate limit admission returned no result");
        }
        return new AttemptReservation(true, key, member);
    }

    public void release(AttemptReservation reservation) {
        if (!reservation.tracked()) {
            return;
        }
        redisTemplate.execute(RELEASE_ATTEMPT_SCRIPT, List.of(reservation.key()),
                reservation.member());
    }

    private String key(String ip) {
        String source = ip == null || ip.isBlank() ? "unknown" : ip;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record AttemptReservation(boolean tracked, String key, String member) {

        private static AttemptReservation notTracked() {
            return new AttemptReservation(false, "", "");
        }
    }
}
