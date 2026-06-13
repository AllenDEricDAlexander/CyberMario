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
        long bloomExpectedInsertions,
        double bloomFalsePositiveProbability
) {

    public RbacCacheProperties {
        apiRulesTtl = apiRulesTtl == null ? Duration.ofMinutes(10) : apiRulesTtl;
        userPermissionsTtl = userPermissionsTtl == null ? Duration.ofMinutes(10) : userPermissionsTtl;
        doubleDeleteDelay = doubleDeleteDelay == null ? Duration.ofMillis(800) : doubleDeleteDelay;
        bloomExpectedInsertions = bloomExpectedInsertions <= 0 ? 100000 : bloomExpectedInsertions;
        bloomFalsePositiveProbability = bloomFalsePositiveProbability <= 0 ? 0.01 : bloomFalsePositiveProbability;
    }

}
