package top.egon.mario.im.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Local and Redis cache settings for immutable IM surface join-key lookups.
 */
@ConfigurationProperties(prefix = "mario.im.surface-join-key-cache")
public record ImSurfaceJoinKeyCacheProperties(
        boolean enabled,
        Duration redisTtl,
        Duration localTtl,
        long localMaximumSize
) {

    public ImSurfaceJoinKeyCacheProperties {
        redisTtl = redisTtl == null ? Duration.ofHours(24) : redisTtl;
        localTtl = localTtl == null ? Duration.ofMinutes(10) : localTtl;
        localMaximumSize = localMaximumSize <= 0 ? 10000 : localMaximumSize;
    }
}
