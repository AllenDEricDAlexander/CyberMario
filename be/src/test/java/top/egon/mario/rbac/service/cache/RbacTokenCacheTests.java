package top.egon.mario.rbac.service.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.egon.mario.rbac.service.security.JwtClaims;
import top.egon.mario.rbac.service.security.JwtTokenPair;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies Redis-backed JWT token state cache behavior.
 */
class RbacTokenCacheTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RbacTokenCache tokenCache;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        tokenCache = new RbacTokenCache(redisTemplate, bloomGuards(), cacheProperties(), mock(RbacRedisCacheInvalidator.class));
    }

    @Test
    void storesAccessAndRefreshTokenStateInRedisWithTokenTtl() {
        JwtTokenPair tokenPair = new JwtTokenPair("access-token", "refresh-token", "access-id", "refresh-id", 60, 3600);

        tokenCache.storeTokenPair(7L, tokenPair, "refresh-hash");

        verify(valueOperations).set("rbac:token:access:access-id", "7", Duration.ofSeconds(60));
        verify(valueOperations).set("rbac:token:refresh:refresh-id", "refresh-hash", Duration.ofSeconds(3600));
    }

    @Test
    void validatesAccessTokenAgainstRedisWhenBloomMayContainTokenId() {
        JwtTokenPair tokenPair = new JwtTokenPair("access-token", "refresh-token", "access-id", "refresh-id", 60, 3600);
        tokenCache.storeTokenPair(7L, tokenPair, "refresh-hash");
        given(redisTemplate.hasKey("rbac:token:access:access-id")).willReturn(true);

        boolean active = tokenCache.isAccessTokenActive(new JwtClaims(7L, "mario", "access-id", "access", Instant.now().plusSeconds(60)));

        assertThat(active).isTrue();
    }

    @Test
    void rejectsUnknownAccessTokenWithoutTouchingRedisWhenBloomDoesNotContainTokenId() {
        boolean active = tokenCache.isAccessTokenActive(new JwtClaims(7L, "mario", "missing-id", "access", Instant.now().plusSeconds(60)));

        assertThat(active).isFalse();
        verify(redisTemplate, never()).hasKey("rbac:token:access:missing-id");
    }

    @Test
    void readsRefreshTokenHashFromRedisWhenBloomMayContainTokenId() {
        JwtTokenPair tokenPair = new JwtTokenPair("access-token", "refresh-token", "access-id", "refresh-id", 60, 3600);
        tokenCache.storeTokenPair(7L, tokenPair, "refresh-hash");
        given(valueOperations.get("rbac:token:refresh:refresh-id")).willReturn("refresh-hash");

        Optional<String> tokenHash = tokenCache.findRefreshTokenHash(new JwtClaims(7L, "mario", "refresh-id", "refresh", Instant.now().plusSeconds(3600)));

        assertThat(tokenHash).contains("refresh-hash");
    }

    private RbacBloomGuards bloomGuards() {
        return new RbacBloomGuards(cacheProperties());
    }

    private RbacCacheProperties cacheProperties() {
        return new RbacCacheProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMillis(10), 1000, 0.01);
    }

}
