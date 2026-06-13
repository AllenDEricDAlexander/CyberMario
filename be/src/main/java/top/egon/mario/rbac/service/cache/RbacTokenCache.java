package top.egon.mario.rbac.service.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.egon.mario.common.utils.LogUtil;
import top.egon.mario.rbac.service.security.JwtClaims;
import top.egon.mario.rbac.service.security.JwtTokenPair;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed token state cache for JWT access and refresh token IDs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RbacTokenCache {

    public static final String ACCESS_TOKEN_PREFIX = "rbac:token:access:";
    public static final String REFRESH_TOKEN_PREFIX = "rbac:token:refresh:";
    private static final String TOKEN_SCAN_PATTERN = "rbac:token:*";

    private final StringRedisTemplate redisTemplate;
    private final RbacBloomGuards bloomGuards;
    private final RbacCacheProperties cacheProperties;
    private final RbacRedisCacheInvalidator invalidator;

    @PostConstruct
    public void warmBloomFromRedis() {
        if (!cacheProperties.enabled()) {
            return;
        }
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(TOKEN_SCAN_PATTERN).count(1000).build())) {
            while (cursor.hasNext()) {
                bloomGuards.rememberTokenKey(cursor.next());
            }
            LogUtil.info(log).log("rbac token cache bloom warmed from redis");
        } catch (RuntimeException e) {
            // Redis may be unavailable during local startup; tokens created later still warm the Bloom filter.
            LogUtil.warn(log).log("rbac token cache bloom warm skipped, reason=redis_unavailable", e);
        }
    }

    public void storeTokenPair(Long userId, JwtTokenPair tokenPair, String refreshTokenHash) {
        if (!cacheProperties.enabled()) {
            return;
        }
        storeAccessToken(tokenPair.accessTokenId(), userId, Duration.ofSeconds(tokenPair.accessTokenExpiresInSeconds()));
        storeRefreshToken(tokenPair.refreshTokenId(), refreshTokenHash, Duration.ofSeconds(tokenPair.refreshTokenExpiresInSeconds()));
    }

    public void storeAccessToken(String tokenId, Long userId, Duration ttl) {
        if (!cacheProperties.enabled()) {
            return;
        }
        store(accessKey(tokenId), String.valueOf(userId), ttl);
    }

    public void storeRefreshToken(String tokenId, String tokenHash, Duration ttl) {
        if (!cacheProperties.enabled()) {
            return;
        }
        store(refreshKey(tokenId), tokenHash, ttl);
    }

    public boolean isAccessTokenActive(JwtClaims claims) {
        if (!cacheProperties.enabled()) {
            return true;
        }
        String key = accessKey(claims.tokenId());
        if (!bloomGuards.mightContainTokenKey(key)) {
            LogUtil.debug(log).log("rbac access token rejected by bloom guard, accessTokenId={}", claims.tokenId());
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Optional<String> findRefreshTokenHash(JwtClaims claims) {
        if (!cacheProperties.enabled()) {
            return Optional.empty();
        }
        String key = refreshKey(claims.tokenId());
        if (!bloomGuards.mightContainTokenKey(key)) {
            LogUtil.debug(log).log("rbac refresh token skipped by bloom guard, refreshTokenId={}", claims.tokenId());
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void evictRefreshToken(String tokenId) {
        if (!cacheProperties.enabled()) {
            return;
        }
        invalidator.doubleDeleteKeys(List.of(refreshKey(tokenId)));
        LogUtil.info(log).log("rbac refresh token cache invalidated, refreshTokenId={}", tokenId);
    }

    public void evictAccessToken(String tokenId) {
        if (!cacheProperties.enabled()) {
            return;
        }
        invalidator.doubleDeleteKeys(List.of(accessKey(tokenId)));
        LogUtil.info(log).log("rbac access token cache invalidated, accessTokenId={}", tokenId);
    }

    public String accessKey(String tokenId) {
        return ACCESS_TOKEN_PREFIX + tokenId;
    }

    public String refreshKey(String tokenId) {
        return REFRESH_TOKEN_PREFIX + tokenId;
    }

    private void store(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        bloomGuards.rememberTokenKey(key);
        redisTemplate.opsForValue().set(key, value, ttl);
        LogUtil.debug(log).log("rbac token cache stored, key={}, ttlSeconds={}", key, ttl.toSeconds());
    }

}
