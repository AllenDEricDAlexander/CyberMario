package top.egon.mario.investment.trading.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentContractSpecPo;
import top.egon.mario.investment.marketdata.repository.InvestmentContractSpecRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.FundingRateJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.MarketBarJdbcRepository;
import top.egon.mario.investment.marketdata.repository.jdbc.model.FundingRateRow;
import top.egon.mario.investment.marketdata.repository.jdbc.model.MarketBarIntradayRow;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.matching.PaperMarketSnapshotReader;
import top.egon.mario.investment.trading.service.model.PaperAcceptanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperFundingJobInput;
import top.egon.mario.investment.trading.service.model.PaperFundingMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMaintenanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;
import top.egon.mario.investment.trading.service.model.PositionMarkSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads maintenance market facts without holding paper-account locks. */
@Component
@RequiredArgsConstructor
public class PaperMaintenanceMarketReader {

    private final FundingRateJdbcRepository fundingRepository;
    private final MarketBarJdbcRepository barRepository;
    private final InvestmentContractSpecRepository contractSpecRepository;
    private final InvestmentPositionRepository positionRepository;
    private final PaperMarketSnapshotReader marketSnapshotReader;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperFundingMarketSnapshot funding(PaperFundingJobInput input) {
        FundingRateRow funding = fundingRepository.findCurrent(
                        input.sourceId(), input.instrumentId(), input.fundingTime(),
                        input.fundingTime().plusNanos(1), 0, 2)
                .stream().filter(value -> value.fundingTime().equals(input.fundingTime()))
                .findFirst().orElseThrow(() -> unavailable("Funding rate is unavailable"));
        MarketBarIntradayRow mark = barRepository.findCurrentIntraday(
                        input.sourceId(), input.instrumentId(), PriceType.MARK, BarInterval.M1,
                        input.fundingTime().minusSeconds(120), input.fundingTime().plusNanos(1), 0, 4)
                .stream().filter(value -> value.closed() && !value.closeTime().isAfter(input.fundingTime()))
                .reduce((left, right) -> right)
                .orElseThrow(() -> unavailable("Funding mark price is unavailable"));
        InvestmentContractSpecPo contract = contract(input.sourceId(), input.instrumentId());
        return new PaperFundingMarketSnapshot(
                positive(mark.closePrice(), "Funding mark price"), funding.fundingRate(), funding.revision(),
                positive(contract.getContractMultiplier(), "Contract multiplier"), mark.closeTime());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaperMaintenanceMarketSnapshot margin(PaperMarginCheckJobInput input, BigDecimal quantity) {
        PaperAcceptanceMarketSnapshot snapshot = marketSnapshotReader.read(
                input.instrumentId(), quantity, input.dataAsOf());
        if (snapshot.sourceId() != input.sourceId() || !snapshot.tradable()
                || snapshot.markPrice() == null || snapshot.markPrice().signum() <= 0
                || !snapshot.completeContractTerms() || !snapshot.tierAvailable()) {
            throw unavailable("Complete mark, contract, and tier facts are unavailable");
        }
        return new PaperMaintenanceMarketSnapshot(
                snapshot.markPrice(), snapshot.quoteSourceTime(), snapshot.quoteVersion(),
                snapshot.priceStep(), snapshot.quantityStep(), snapshot.contractMultiplier(),
                snapshot.takerFeeRate(), snapshot.slippageBps(), snapshot.maintenanceMarginRate(),
                snapshot.tierObservedAt());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<Long, PositionMarkSnapshot> accountMarks(long accountId, Instant dataAsOf) {
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdOrderByInstrumentIdAsc(accountId);
        Map<Long, PositionMarkSnapshot> marks = new LinkedHashMap<>();
        for (InvestmentPositionPo position : positions) {
            PaperAcceptanceMarketSnapshot snapshot = marketSnapshotReader.read(
                    position.getInstrumentId(), position.getQuantity(), dataAsOf);
            if (snapshot.markPrice() == null || snapshot.markPrice().signum() <= 0
                    || snapshot.contractMultiplier() == null || snapshot.contractMultiplier().signum() <= 0) {
                throw unavailable("Account mark snapshot is incomplete");
            }
            marks.put(position.getInstrumentId(), new PositionMarkSnapshot(
                    position.getInstrumentId(), snapshot.markPrice(), snapshot.contractMultiplier()));
        }
        return Map.copyOf(marks);
    }

    private InvestmentContractSpecPo contract(long sourceId, long instrumentId) {
        return contractSpecRepository.findById(instrumentId)
                .filter(value -> value.getSourceId() == sourceId)
                .orElseThrow(() -> unavailable("Contract specification is unavailable"));
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw unavailable(name + " must be positive");
        }
        return value;
    }

    private static InvestmentException unavailable(String message) {
        return new InvestmentException(InvestmentErrorCode.DATA_UNAVAILABLE, message);
    }
}
