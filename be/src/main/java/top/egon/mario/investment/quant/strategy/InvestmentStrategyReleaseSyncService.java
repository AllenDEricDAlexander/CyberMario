package top.egon.mario.investment.quant.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;
import top.egon.mario.investment.quant.repository.InvestmentStrategyReleaseRepository;

import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Synchronizes code strategies to immutable release snapshots before quant scheduling begins.
 */
@Service
@Order(Ordered.LOWEST_PRECEDENCE - 30)
public class InvestmentStrategyReleaseSyncService implements ApplicationRunner {

    private final InvestmentStrategyReleaseRepository repository;
    private final InvestmentStrategyRegistry registry;
    private final ObjectMapper canonicalMapper;
    private final InvestmentStrategySourceHasher sourceHasher;
    private final Clock clock;

    public InvestmentStrategyReleaseSyncService(InvestmentStrategyReleaseRepository repository,
                                                InvestmentStrategyRegistry registry,
                                                ObjectMapper objectMapper,
                                                InvestmentStrategySourceHasher sourceHasher,
                                                Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.canonicalMapper = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.sourceHasher = sourceHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        synchronize();
    }

    @Transactional
    public void synchronize() {
        Set<ReleaseKey> installed = new HashSet<>();
        for (InvestmentStrategy strategy : registry.strategies()) {
            StrategyDescriptor descriptor = strategy.descriptor();
            ReleaseKey key = new ReleaseKey(descriptor.strategyCode(), descriptor.strategyVersion());
            installed.add(key);
            StrategySourceFingerprint fingerprint = sourceHasher.fingerprint(strategy.getClass());
            String descriptorJson = json(descriptor);
            String capabilitiesJson = json(descriptor.requiredCapabilities());
            repository.findByStrategyCodeAndStrategyVersion(key.strategyCode(), key.strategyVersion())
                    .ifPresentOrElse(existing -> validateExisting(existing, strategy, fingerprint, descriptorJson),
                            () -> repository.saveAndFlush(release(
                                    strategy, fingerprint, descriptorJson, capabilitiesJson)));
        }
        for (InvestmentStrategyReleasePo release : repository.findAllByActiveTrue()) {
            ReleaseKey key = new ReleaseKey(release.getStrategyCode(), release.getStrategyVersion());
            if (!installed.contains(key)) {
                release.setActive(false);
                repository.saveAndFlush(release);
            }
        }
    }

    private InvestmentStrategyReleasePo release(InvestmentStrategy strategy, StrategySourceFingerprint fingerprint,
                                                 String descriptorJson, String capabilitiesJson) {
        StrategyDescriptor descriptor = strategy.descriptor();
        InvestmentStrategyReleasePo release = new InvestmentStrategyReleasePo();
        release.setStrategyCode(descriptor.strategyCode());
        release.setStrategyVersion(descriptor.strategyVersion());
        release.setDisplayName(descriptor.displayName());
        release.setDescription(descriptor.description());
        release.setImplementationClass(strategy.getClass().getName());
        release.setEngineType(descriptor.engineType().name());
        release.setBuildRevision(fingerprint.buildRevision());
        release.setSourceHash(fingerprint.sourceHash());
        release.setRequiredCapabilitiesJson(capabilitiesJson);
        release.setDescriptorSnapshotJson(descriptorJson);
        release.setActive(true);
        release.setRegisteredAt(clock.instant());
        return release;
    }

    private void validateExisting(InvestmentStrategyReleasePo existing, InvestmentStrategy strategy,
                                  StrategySourceFingerprint fingerprint, String descriptorJson) {
        StrategyDescriptor descriptor = strategy.descriptor();
        if (!Objects.equals(existing.getSourceHash(), fingerprint.sourceHash())) {
            throw changed(descriptor, "source hash changed");
        }
        if (!Objects.equals(existing.getImplementationClass(), strategy.getClass().getName())
                || !Objects.equals(existing.getEngineType(), descriptor.engineType().name())
                || !jsonEquivalent(existing.getDescriptorSnapshotJson(), descriptorJson)) {
            throw changed(descriptor, "descriptor or implementation changed");
        }
        if (!existing.isActive()) {
            existing.setActive(true);
            repository.saveAndFlush(existing);
        }
    }

    private boolean jsonEquivalent(String first, String second) {
        try {
            return Objects.equals(canonicalMapper.readTree(first), canonicalMapper.readTree(second));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted strategy descriptor JSON is invalid", exception);
        }
    }

    private IllegalStateException changed(StrategyDescriptor descriptor, String reason) {
        return new IllegalStateException("Investment strategy " + descriptor.strategyCode() + "/"
                + descriptor.strategyVersion() + " " + reason + "; increment strategyVersion");
    }

    private String json(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to encode strategy release snapshot", exception);
        }
    }

    private record ReleaseKey(String strategyCode, String strategyVersion) {
    }
}
