package top.egon.mario.rbac.service.cache;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Bloom filters used as lightweight guards before Redis cache lookups.
 */
@Component
@Slf4j
public class RbacBloomGuards {

    private final BloomFilter<CharSequence> tokenKeyBloom;
    private final BloomFilter<CharSequence> permissionKeyBloom;

    public RbacBloomGuards(RbacCacheProperties cacheProperties) {
        long expectedInsertions = Math.max(cacheProperties.bloomExpectedInsertions(), 1000);
        double falsePositiveProbability = cacheProperties.bloomFalsePositiveProbability() <= 0
                ? 0.01
                : cacheProperties.bloomFalsePositiveProbability();
        this.tokenKeyBloom = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions, falsePositiveProbability);
        this.permissionKeyBloom = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions, falsePositiveProbability);
        log.info("rbac bloom guards initialized, expectedInsertions={}, falsePositiveProbability={}",
                expectedInsertions, falsePositiveProbability);
    }

    public synchronized void rememberTokenKey(String key) {
        tokenKeyBloom.put(key);
    }

    public synchronized boolean mightContainTokenKey(String key) {
        return tokenKeyBloom.mightContain(key);
    }

    public synchronized void rememberPermissionKey(String key) {
        permissionKeyBloom.put(key);
    }

    public synchronized boolean mightContainPermissionKey(String key) {
        return permissionKeyBloom.mightContain(key);
    }

}
