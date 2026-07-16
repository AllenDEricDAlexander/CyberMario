package top.egon.mario.investment.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.job.InvestmentJobClaim;
import top.egon.mario.investment.common.job.InvestmentJobNonRetryableException;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.InvestmentJobType;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.po.InvestmentDataQualityIssuePo;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimension;
import top.egon.mario.investment.marketdata.ingest.MarketDataDimensionResolver;
import top.egon.mario.investment.marketdata.ingest.MarketDataJobInput;
import top.egon.mario.investment.marketdata.ingest.handler.DataQualityCheckJobHandler;
import top.egon.mario.investment.marketdata.provider.model.ExternalCandle;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityCode;
import top.egon.mario.investment.marketdata.quality.MarketDataQualityService;
import top.egon.mario.investment.marketdata.repository.InvestmentDataQualityIssueRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.marketdata.subscription.MarketSubscription;
import top.egon.mario.investment.marketdata.subscription.RetentionPolicy;
import top.egon.mario.investment.marketdata.subscription.SubscriptionSchedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvestmentMarketDataQualityTests {

    private final InvestmentDataQualityIssueRepository issueRepository =
            mock(InvestmentDataQualityIssueRepository.class);
    private final MarketDataQualityService service = new MarketDataQualityService(issueRepository,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void detectsDuplicateGapStaleAndEveryMissingContractInputWithoutZeroFallbacks() {
        ExternalCandle first = candle("2026-07-16T00:00:00Z");
        ExternalCandle duplicate = candle("2026-07-16T00:00:00Z");
        ExternalCandle gap = candle("2026-07-16T00:03:00Z");

        assertThat(service.inspectCandles(List.of(gap, duplicate, first))).extracting(finding -> finding.code())
                .contains(MarketDataQualityCode.DUPLICATE, MarketDataQualityCode.GAP);
        assertThat(service.missingContractInputs(Instant.parse("2026-07-16T00:00:00Z"), false, false, false))
                .extracting(finding -> finding.code())
                .containsExactly(MarketDataQualityCode.MISSING_MARK_PRICE,
                        MarketDataQualityCode.MISSING_FUNDING_RATE,
                        MarketDataQualityCode.MISSING_POSITION_TIER);
        assertThat(service.staleQuote(Instant.parse("2026-07-15T23:00:00Z"),
                Instant.parse("2026-07-15T23:59:00Z")).code()).isEqualTo(MarketDataQualityCode.STALE_QUOTE);
    }

    @Test
    void providerModelRejectsInvalidOhlcAndNegativeVolumeBeforePersistence() {
        assertThatThrownBy(() -> new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.M1, Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:01:00Z"), decimal("100"), decimal("99"), decimal("98"),
                decimal("100"), decimal("1"), decimal("1"), true, Instant.parse("2026-07-16T00:01:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                PriceType.MARKET, BarInterval.M1, Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:01:00Z"), decimal("100"), decimal("101"), decimal("99"),
                decimal("100"), decimal("-1"), decimal("1"), true, Instant.parse("2026-07-16T00:01:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void persistsStableIssueCodesAsAuditedFacts() {
        service.persist(77, new MarketDataQualityService.MarketDataDimensionRef(10, 20),
                service.missingContractInputs(Instant.parse("2026-07-16T00:00:00Z"), false, false, false));

        ArgumentCaptor<InvestmentDataQualityIssuePo> captor = ArgumentCaptor.forClass(
                InvestmentDataQualityIssuePo.class);
        verify(issueRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(InvestmentDataQualityIssuePo::getIssueCode)
                .containsExactly("MISSING_MARK_PRICE", "MISSING_FUNDING_RATE", "MISSING_POSITION_TIER");
        assertThat(captor.getAllValues()).allSatisfy(issue -> {
            assertThat(issue.getJobId()).isEqualTo(77);
            assertThat(issue.getDetailsJson()).contains("zeroFallbackAllowed");
        });
    }

    @Test
    void revokedQualityJobIsAuditedWithSanitizedShapeAndFailsPermanently() throws Exception {
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        MarketDataDimensionResolver dimensionResolver = mock(MarketDataDimensionResolver.class);
        when(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA)).thenThrow(new InvestmentException(
                InvestmentErrorCode.SUBSCRIPTION_REJECTED, "revoked"));
        when(dimensionResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        DataQualityCheckJobHandler handler = new DataQualityCheckJobHandler(
                new ObjectMapper().findAndRegisterModules(), registry, dimensionResolver,
                mock(ContractQuoteJdbcRepository.class), mock(NamedParameterJdbcTemplate.class), service,
                new TransactionTemplate(new NoOpTransactionManager()),
                java.time.Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), java.time.ZoneOffset.UTC));
        MarketDataJobInput input = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA, PriceType.NONE, BarInterval.NONE, null, null, 1000);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(input);
        InvestmentJobClaim claim = new InvestmentJobClaim(88, null, InvestmentJobType.DATA_QUALITY_CHECK,
                json, 1, 5, "worker", "token", Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T00:01:00Z"));

        assertThatThrownBy(() -> handler.execute(claim)).isInstanceOf(InvestmentJobNonRetryableException.class)
                .hasMessageContaining("revoked");
        ArgumentCaptor<InvestmentDataQualityIssuePo> captor = ArgumentCaptor.forClass(
                InvestmentDataQualityIssuePo.class);
        verify(issueRepository).save(captor.capture());
        assertThat(captor.getValue().getIssueCode()).isEqualTo("OUT_OF_SUBSCRIPTION");
        assertThat(captor.getValue().getDetailsJson()).contains("CONTRACT_METADATA", "NONE")
                .doesNotContain("worker", "token");
    }

    @Test
    void qualityCheckUsesLatestTickerRatherThanMarkCandleAsTheSharedQuoteMarkPolicy() throws Exception {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        InvestmentMarketSubscriptionRegistry registry = mock(InvestmentMarketSubscriptionRegistry.class);
        MarketDataDimensionResolver resolver = mock(MarketDataDimensionResolver.class);
        when(resolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new MarketDataDimension(10, 20));
        ContractQuoteJdbcRepository quoteRepository = mock(ContractQuoteJdbcRepository.class);
        when(quoteRepository.findLatest(10, 20)).thenReturn(Optional.of(new ContractQuoteRow(
                10, 20, decimal("100"), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, now, now, 0)));
        MarketSubscription latestTicker = subscription(Set.of(DataCapability.CONTRACT_METADATA,
                DataCapability.LATEST_TICKER));
        MarketSubscription markCandleOnly = subscription(Set.of(DataCapability.CONTRACT_METADATA,
                DataCapability.MARK_CANDLE));
        MarketSubscription openInterestOnly = subscription(Set.of(DataCapability.CONTRACT_METADATA,
                DataCapability.OPEN_INTEREST));
        when(registry.requireCapability("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA)).thenReturn(latestTicker, markCandleOnly, openInterestOnly);
        DataQualityCheckJobHandler handler = new DataQualityCheckJobHandler(
                new ObjectMapper().findAndRegisterModules(), registry, resolver, quoteRepository,
                mock(NamedParameterJdbcTemplate.class), service, new TransactionTemplate(new NoOpTransactionManager()),
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        MarketDataJobInput input = new MarketDataJobInput("TEST", ProductType.USDT_FUTURES, "BTCUSDT",
                DataCapability.CONTRACT_METADATA, PriceType.NONE, BarInterval.NONE, null, null, 1000);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(input);
        InvestmentJobClaim claim = new InvestmentJobClaim(89, null, InvestmentJobType.DATA_QUALITY_CHECK,
                json, 1, 5, "worker", "token", now, now.plusSeconds(60));

        handler.execute(claim);

        verify(issueRepository).save(org.mockito.ArgumentMatchers.argThat(issue ->
                issue.getIssueCode().equals("MISSING_MARK_PRICE")));

        clearInvocations(issueRepository);
        handler.execute(claim);

        verifyNoInteractions(issueRepository);

        handler.execute(claim);

        verify(issueRepository).save(org.mockito.ArgumentMatchers.argThat(issue ->
                issue.getIssueCode().equals("MISSING_OPEN_INTEREST")));
        assertThat(service.requiresQuoteMarkPrice(Set.of(DataCapability.LATEST_TICKER))).isTrue();
        assertThat(service.requiresQuoteMarkPrice(Set.of(DataCapability.MARK_CANDLE))).isFalse();
        assertThat(service.requiresQuoteOpenInterest(Set.of(DataCapability.OPEN_INTEREST))).isTrue();
        assertThat(service.requiresQuoteOpenInterest(Set.of(DataCapability.LATEST_TICKER))).isFalse();
    }

    private MarketSubscription subscription(Set<DataCapability> capabilities) {
        Map<DataCapability, Duration> refresh = capabilities.stream().collect(java.util.stream.Collectors.toMap(
                capability -> capability, capability -> Duration.ofMinutes(1)));
        return new MarketSubscription("TEST", ProductType.USDT_FUTURES, "BTCUSDT", Set.of(), Set.of(),
                capabilities, new SubscriptionSchedule(refresh, Map.of()),
                new RetentionPolicy(Set.of(), Map.of()));
    }

    private ExternalCandle candle(String openTime) {
        Instant open = Instant.parse(openTime);
        return new ExternalCandle("TEST", ProductType.USDT_FUTURES, "BTCUSDT", PriceType.MARKET,
                BarInterval.M1, open, open.plusSeconds(60), decimal("100"), decimal("101"), decimal("99"),
                decimal("100"), decimal("1"), decimal("100"), true, open.plusSeconds(60));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
