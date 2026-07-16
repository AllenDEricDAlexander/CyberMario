package top.egon.mario.investment.marketdata.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.ingest.AbstractMarketDataJobHandler;
import top.egon.mario.investment.marketdata.ingest.MarketDataChecksum;
import top.egon.mario.investment.marketdata.ingest.MarketDataCursorService;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.event.InvestmentMarketDataCommittedEvent;
import top.egon.mario.investment.marketdata.event.MarketDataAfterCommitPublisher;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.provider.PositionTierProvider;
import top.egon.mario.investment.marketdata.provider.ProviderRegistry;
import top.egon.mario.investment.marketdata.provider.model.ExternalPositionTier;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.InvestmentPositionTierRepository;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Persists immutable position-tier sets only when their normalized content hash changes.
 */
@Component
public class PositionTierSyncJobHandler extends AbstractMarketDataJobHandler<ExternalPositionTier> {

    private final ProviderRegistry providerRegistry;
    private final MarketDataDimensionResolver dimensionResolver;
    private final InvestmentPositionTierRepository tierRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MarketDataCursorService cursorService;
    private final MarketDataQualityService qualityService;
    private final MarketDataAfterCommitPublisher afterCommitPublisher;

    public PositionTierSyncJobHandler(ObjectMapper objectMapper,
                                      InvestmentMarketSubscriptionRegistry subscriptionRegistry,
                                      TransactionTemplate transactionTemplate,
                                      ProviderRegistry providerRegistry,
                                      MarketDataDimensionResolver dimensionResolver,
                                      InvestmentPositionTierRepository tierRepository,
                                      NamedParameterJdbcTemplate jdbcTemplate,
                                      MarketDataCursorService cursorService,
                                      MarketDataQualityService qualityService,
                                      MarketDataAfterCommitPublisher afterCommitPublisher) {
        super(objectMapper, subscriptionRegistry, transactionTemplate, dimensionResolver, qualityService);
        this.providerRegistry = providerRegistry;
        this.dimensionResolver = dimensionResolver;
        this.tierRepository = tierRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.cursorService = cursorService;
        this.qualityService = qualityService;
        this.afterCommitPublisher = afterCommitPublisher;
    }

    @Override
    public InvestmentJobType jobType() {
        return InvestmentJobType.POSITION_TIER_SYNC;
    }

    @Override
    protected List<ExternalPositionTier> fetch(MarketDataJobInput input) {
        return providerRegistry.require(input.sourceCode(), DataCapability.POSITION_TIER,
                PositionTierProvider.class).positionTiers(input.productType(), input.symbol());
    }

    @Override
    protected void validateSubscription(MarketDataJobInput input) {
        if (input.capability() != DataCapability.POSITION_TIER) {
            throw new InvestmentJobNonRetryableException("MARKET_JOB_CAPABILITY_INVALID",
                    "Position-tier handler requires POSITION_TIER");
        }
        subscriptionRegistry().requireCapability(input.sourceCode(), input.productType(), input.symbol(),
                DataCapability.POSITION_TIER);
    }

    @Override
    protected void validatePage(MarketDataJobInput input, List<ExternalPositionTier> page) {
        int expectedTier = 1;
        BigDecimal previousEnd = null;
        Instant observedAt = null;
        for (ExternalPositionTier tier : page.stream().sorted(Comparator.comparingInt(ExternalPositionTier::tier))
                .toList()) {
            if (!input.sourceCode().equals(tier.sourceCode()) || input.productType() != tier.productType()
                    || !input.symbol().equals(tier.symbol())) {
                throw new InvestmentJobNonRetryableException("MARKET_PROVIDER_DIMENSION_MISMATCH",
                        "Provider returned a position tier outside the requested subscription");
            }
            if (tier.tier() != expectedTier++
                    || previousEnd != null && previousEnd.compareTo(tier.minimumNotional()) != 0) {
                throw new InvestmentJobNonRetryableException("MARKET_POSITION_TIER_INVALID",
                        "Position tiers must be contiguous and ordered from tier one");
            }
            if (observedAt != null && !observedAt.equals(tier.observedAt())) {
                throw new InvestmentJobNonRetryableException("MARKET_POSITION_TIER_MIXED_OBSERVED_AT",
                        "A position-tier snapshot must have one observedAt");
            }
            observedAt = tier.observedAt();
            previousEnd = tier.maximumNotional();
        }
    }

    @Override
    protected int persistPage(InvestmentJobClaim claim, MarketDataJobInput input, List<ExternalPositionTier> page,
                              List<ExternalPositionTier> previousPage) {
        MarketDataDimension dimension = dimensionResolver.resolve(input);
        MarketDataCursorService.LockedCursor cursor = cursorService.lock(dimension, "POSITION_TIER",
                PriceType.NONE, BarInterval.NONE);
        if (page.isEmpty()) {
            qualityService.persist(claim.id(), new MarketDataQualityService.MarketDataDimensionRef(
                            dimension.sourceId(), dimension.instrumentId()),
                    qualityService.missingContractInputs(cursor.completedAt(), true, true, false));
            return 0;
        }
        List<ExternalPositionTier> ordered = page.stream()
                .sorted(Comparator.comparingInt(ExternalPositionTier::tier)).toList();
        Instant observedAt = ordered.getFirst().observedAt();
        String hash = MarketDataChecksum.sha256(ordered.stream().map(this::hashValue)
                .reduce((left, right) -> left + "|" + right).orElseThrow());
        CurrentSnapshot current = currentSnapshot(dimension);
        int written;
        if (current != null && observedAt.equals(current.observedAt()) && !hash.equals(current.hash())) {
            throw new InvestmentJobNonRetryableException("MARKET_POSITION_TIER_OBSERVED_AT_CONFLICT",
                    "Position-tier content changed at an already persisted observedAt");
        }
        if (current != null && observedAt.isBefore(current.observedAt())) {
            throw new InvestmentJobNonRetryableException("MARKET_POSITION_TIER_STALE_SNAPSHOT",
                    "Position-tier observedAt moved backwards");
        }
        Instant lastSeenAt = cursor.completedAt().isBefore(observedAt) ? observedAt : cursor.completedAt();
        boolean changed = current == null || !hash.equals(current.hash());
        if (current != null && hash.equals(current.hash())) {
            jdbcTemplate.update("""
                    update investment_position_tier set last_seen_at = greatest(last_seen_at, :lastSeenAt)
                    where source_id = :sourceId and instrument_id = :instrumentId
                      and observed_at = (select max(observed_at) from investment_position_tier
                          where source_id = :sourceId and instrument_id = :instrumentId)
                    """, new MapSqlParameterSource()
                    .addValue("lastSeenAt", OffsetDateTime.ofInstant(lastSeenAt, ZoneOffset.UTC))
                    .addValue("sourceId", dimension.sourceId())
                    .addValue("instrumentId", dimension.instrumentId()));
            written = 0;
        } else {
            tierRepository.saveAll(ordered.stream().map(tier -> po(dimension, tier, observedAt,
                    cursor.completedAt(),
                    lastSeenAt, hash)).toList());
            written = ordered.size();
        }
        cursorService.completeLocked(cursor, null, hash);
        if (changed) {
            afterCommitPublisher.publishAfterCommit(new InvestmentMarketDataCommittedEvent(dimension.sourceId(),
                    dimension.instrumentId(), "POSITION_TIER", written, observedAt));
        }
        return written;
    }

    private CurrentSnapshot currentSnapshot(MarketDataDimension dimension) {
        List<CurrentSnapshot> values = jdbcTemplate.query("""
                select observed_at, source_hash from investment_position_tier
                where source_id = :sourceId and instrument_id = :instrumentId
                order by observed_at desc, tier_level asc limit 1
                """, Map.of("sourceId", dimension.sourceId(), "instrumentId", dimension.instrumentId()),
                (resultSet, rowNum) -> new CurrentSnapshot(resultSet.getObject("observed_at", OffsetDateTime.class)
                        .toInstant(), resultSet.getString("source_hash")));
        return values.isEmpty() ? null : values.getFirst();
    }

    private InvestmentPositionTierPo po(MarketDataDimension dimension, ExternalPositionTier tier,
                                        Instant observedAt, Instant ingestedAt, Instant lastSeenAt, String hash) {
        InvestmentPositionTierPo value = new InvestmentPositionTierPo();
        value.setSourceId(dimension.sourceId());
        value.setInstrumentId(dimension.instrumentId());
        value.setObservedAt(observedAt);
        value.setTierLevel(tier.tier());
        value.setStartNotional(tier.minimumNotional());
        value.setEndNotional(tier.maximumNotional());
        value.setMaxLeverage(BigDecimal.valueOf(tier.maximumLeverage()));
        value.setMaintenanceMarginRate(tier.maintenanceMarginRatio());
        value.setSourceHash(hash);
        value.setIngestedAt(ingestedAt);
        value.setLastSeenAt(lastSeenAt);
        return value;
    }

    private String hashValue(ExternalPositionTier tier) {
        return tier.tier() + ":" + MarketDataChecksum.decimal(tier.minimumNotional()) + ":"
                + MarketDataChecksum.decimal(tier.maximumNotional()) + ":"
                + MarketDataChecksum.decimal(tier.maintenanceMarginRatio()) + ":" + tier.maximumLeverage();
    }

    private record CurrentSnapshot(Instant observedAt, String hash) {
    }
}
