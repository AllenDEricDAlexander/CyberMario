package top.egon.mario.rbac.service.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis and Bloom filter settings for RBAC runtime caches.
 */
@ConfigurationProperties(prefix = "mario.rbac.cache")
public record RbacCacheProperties(
        boolean enabled,
        Duration apiRulesTtl,
        Duration userPermissionsTtl,
        Duration doubleDeleteDelay,
        Duration localTtl,
        long localMaximumSize,
        long bloomExpectedInsertions,
        double bloomFalsePositiveProbability,
        boolean broadcastEnabled,
        String broadcastTopic
) {

    public RbacCacheProperties {
        apiRulesTtl = apiRulesTtl == null ? Duration.ofMinutes(10) : apiRulesTtl;
        userPermissionsTtl = userPermissionsTtl == null ? Duration.ofMinutes(10) : userPermissionsTtl;
        doubleDeleteDelay = doubleDeleteDelay == null ? Duration.ofMillis(800) : doubleDeleteDelay;
        localTtl = localTtl == null ? Duration.ofSeconds(30) : localTtl;
        localMaximumSize = localMaximumSize <= 0 ? 10000 : localMaximumSize;
        bloomExpectedInsertions = bloomExpectedInsertions <= 0 ? 100000 : bloomExpectedInsertions;
        bloomFalsePositiveProbability = bloomFalsePositiveProbability <= 0 ? 0.01 : bloomFalsePositiveProbability;
        broadcastTopic = broadcastTopic == null || broadcastTopic.isBlank() ? "rbac:cache:evict" : broadcastTopic;
    }

}
