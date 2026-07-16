package top.egon.mario.investment.trading.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.DataCapability;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentDataSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentInstrumentSourceRepository;
import top.egon.mario.investment.marketdata.repository.InvestmentPositionTierRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.ContractQuoteJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.ContractQuoteRow;
import top.egon.mario.investment.marketdata.subscription.InvestmentMarketSubscriptionRegistry;
import top.egon.mario.investment.trading.service.model.PaperAcceptanceMarketSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reads all acceptance market facts before any paper-account lock is acquired.
 */
@Component
@RequiredArgsConstructor
public class PaperMarketSnapshotReader {

    private static final BigDecimal DEFAULT_SLIPPAGE_BPS = new BigDecimal("5");

    private final InvestmentInstrumentRepository instrumentRepository;
    private final InvestmentInstrumentSourceRepository instrumentSourceRepository;
    private final InvestmentDataSourceRepository dataSourceRepository;
    private final InvestmentContractSpecRepository contractSpecRepository;
    private final InvestmentPositionTierRepository tierRepository;
    private final ContractQuoteJdbcRepository quoteRepository;
    private final InvestmentMarketSubscriptionRegistry subscriptionRegistry;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperAcceptanceMarketSnapshot read(long instrumentId, BigDecimal quantity, Instant dataAsOf) {
        InvestmentInstrumentPo instrument = instrumentRepository.findById(instrumentId).orElse(null);
        var mapping = instrumentSourceRepository
                .findFirstByInstrumentIdAndSourceStatusAndDeletedFalseOrderByIdAsc(instrumentId, "ACTIVE")
                .orElse(null);
        long sourceId = mapping == null ? 0L : mapping.getSourceId();
        InvestmentDataSourcePo source = sourceId <= 0 ? null : dataSourceRepository.findById(sourceId).orElse(null);
        InvestmentContractSpecPo specification = sourceId <= 0 ? null : contractSpecRepository.findById(instrumentId)
                .filter(value -> value.getSourceId() == sourceId)
                .orElse(null);
        ContractQuoteRow quote = sourceId <= 0 ? null : quoteRepository.findLatest(sourceId, instrumentId).orElse(null);
        BigDecimal notional = quote == null || quote.markPrice() == null || specification == null
                ? BigDecimal.ZERO
                : quantity.multiply(quote.markPrice()).multiply(specification.getContractMultiplier()).abs();
        InvestmentPositionTierPo tier = latestTier(sourceId, instrumentId, dataAsOf, notional);
        boolean subscribed = subscribed(source, instrument);
        boolean tradable = instrument != null && !instrument.isDeleted() && "ACTIVE".equals(instrument.getStatus())
                && source != null && !source.isDeleted() && "ACTIVE".equals(source.getStatus())
                && specification != null;
        return new PaperAcceptanceMarketSnapshot(
                sourceId, instrumentId, subscribed, tradable,
                quote == null ? null : quote.markPrice(), quote == null ? null : quote.sourceTime(),
                quote == null ? -1L : quote.version(), specification == null ? -1L : specification.getRevision(),
                specification == null ? null : specification.getPriceEndStep(),
                specification == null ? null : specification.getQuantityStep(),
                specification == null ? null : specification.getContractMultiplier(),
                specification == null ? null : specification.getMakerFeeRate(),
                specification == null ? null : specification.getTakerFeeRate(),
                specification == null ? null : specification.getMaxLeverage(),
                tier == null ? null : tier.getObservedAt(), tier == null ? null : tier.getMaxLeverage(),
                tier == null ? null : tier.getMaintenanceMarginRate(),
                quote != null && quote.fundingRate() != null, DEFAULT_SLIPPAGE_BPS);
    }

    private InvestmentPositionTierPo latestTier(
            long sourceId, long instrumentId, Instant dataAsOf, BigDecimal notional) {
        if (sourceId <= 0) {
            return null;
        }
        return tierRepository
                .findFirstBySourceIdAndInstrumentIdAndObservedAtLessThanEqualOrderByObservedAtDesc(
                        sourceId, instrumentId, dataAsOf)
                .map(latest -> tierRepository.findBySourceIdAndInstrumentIdAndObservedAtOrderByTierLevel(
                        sourceId, instrumentId, latest.getObservedAt()))
                .orElseGet(List::of).stream()
                .filter(value -> notional.compareTo(value.getStartNotional()) >= 0
                        && notional.compareTo(value.getEndNotional()) < 0)
                .findFirst()
                .orElse(null);
    }

    private boolean subscribed(InvestmentDataSourcePo source, InvestmentInstrumentPo instrument) {
        if (source == null || instrument == null) {
            return false;
        }
        try {
            subscriptionRegistry.requireCapability(
                    source.getCode(), instrument.getProductType(), instrument.getSymbol(), DataCapability.LATEST_TICKER);
            subscriptionRegistry.requireCapability(
                    source.getCode(), instrument.getProductType(), instrument.getSymbol(), DataCapability.FUNDING_RATE);
            subscriptionRegistry.requireCapability(
                    source.getCode(), instrument.getProductType(), instrument.getSymbol(), DataCapability.POSITION_TIER);
            subscriptionRegistry.requireCandle(source.getCode(), instrument.getProductType(), instrument.getSymbol(),
                    BarInterval.M1, PriceType.MARKET);
            return true;
        } catch (InvestmentException exception) {
            return false;
        }
    }
}
