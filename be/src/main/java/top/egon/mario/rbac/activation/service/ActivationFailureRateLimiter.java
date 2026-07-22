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

    private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count == 0 then redis.call('DEL', KEYS[1]) end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RbacOttProperties properties;
    private final Clock clock;

    public void assertAllowed(String ip) {
        if (!properties.rateLimitEnabled()) {
            return;
        }
        long now = clock.millis();
        Long count = redisTemplate.execute(COUNT_SCRIPT, List.of(key(ip)),
                String.valueOf(now - WINDOW.toMillis()));
        if (count != null && count >= FAILURE_LIMIT) {
            throw new RbacException("AUTH_ACTIVATION_RATE_LIMITED",
                    "too many activation failures; try again later");
        }
    }

    public void recordFailure(String ip) {
        if (!properties.rateLimitEnabled()) {
            return;
        }
        long now = clock.millis();
        redisTemplate.execute(RECORD_SCRIPT, List.of(key(ip)),
                String.valueOf(now - WINDOW.toMillis()),
                String.valueOf(now),
                now + ":" + UUID.randomUUID(),
                String.valueOf(WINDOW.toMillis()));
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
}
