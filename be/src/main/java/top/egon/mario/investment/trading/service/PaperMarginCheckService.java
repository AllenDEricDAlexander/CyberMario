package top.egon.mario.investment.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.model.PaperLiquidationResult;
import top.egon.mario.investment.trading.service.model.PaperMaintenanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckJobInput;
import top.egon.mario.investment.trading.service.model.PaperMarginCheckResult;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/** Evaluates the isolated maintenance boundary and delegates liquidation facts when breached. */
@Service
public class PaperMarginCheckService {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final PaperLiquidationService liquidationService;

    public PaperMarginCheckService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            PaperLiquidationService liquidationService) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.liquidationService = liquidationService;
    }

    @Transactional
    public PaperMarginCheckResult check(
            PaperMarginCheckJobInput input, PaperMaintenanceMarketSnapshot market, Instant checkedAt) {
        validate(input, market);
        accountRepository.findByIdAndWorkspaceIdForUpdate(input.accountId(), input.workspaceId())
                .orElseThrow(PaperMarginCheckService::notFound);
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(input.accountId());
        InvestmentPositionPo position = positions.stream()
                .filter(value -> value.getId() == input.positionId()
                        && value.getInstrumentId() == input.instrumentId())
                .findFirst().orElse(null);
        if (position == null) {
            return new PaperMarginCheckResult(
                    input.accountId(), input.positionId(), "POSITION_CLOSED",
                    BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        BigDecimal notional = position.getQuantity().multiply(market.markPrice())
                .multiply(market.contractMultiplier()).abs();
        BigDecimal maintenance = notional.multiply(market.maintenanceMarginRate());
        BigDecimal priceChange = "LONG".equals(position.getPositionSide())
                ? market.markPrice().subtract(position.getEntryPrice())
                : position.getEntryPrice().subtract(market.markPrice());
        BigDecimal unrealized = priceChange.multiply(position.getQuantity())
                .multiply(market.contractMultiplier());
        BigDecimal positionEquity = position.getIsolatedMargin().add(unrealized);
        BigDecimal estimatedCloseFee = notional.multiply(market.takerFeeRate());
        BigDecimal threshold = maintenance.add(estimatedCloseFee);
        position.setMaintenanceMarginRate(market.maintenanceMarginRate());
        position.setMaintenanceMargin(maintenance);
        position.setLiquidationPrice(liquidationPrice(position, market.maintenanceMarginRate()));
        position.setLastMarginCheckAt(checkedAt);
        position.setUpdatedAt(checkedAt);
        if (positionEquity.compareTo(threshold) <= 0) {
            PaperLiquidationResult liquidation = liquidationService.liquidate(input, market, checkedAt);
            return new PaperMarginCheckResult(
                    input.accountId(), input.positionId(),
                    "FILLED".equals(liquidation.status()) ? "LIQUIDATED" : liquidation.status(),
                    positionEquity, threshold, liquidation.orderId());
        }
        positionRepository.saveAndFlush(position);
        return new PaperMarginCheckResult(
                input.accountId(), input.positionId(), "HEALTHY",
                positionEquity, threshold, null);
    }

    private static BigDecimal liquidationPrice(
            InvestmentPositionPo position, BigDecimal maintenanceMarginRate) {
        BigDecimal marginRatio = BigDecimal.ONE.divide(position.getLeverage(), MATH);
        BigDecimal factor = "LONG".equals(position.getPositionSide())
                ? BigDecimal.ONE.subtract(marginRatio).add(maintenanceMarginRate)
                : BigDecimal.ONE.add(marginRatio).subtract(maintenanceMarginRate);
        return position.getEntryPrice().multiply(factor).max(new BigDecimal("0.000000000000000001"));
    }

    private static void validate(PaperMarginCheckJobInput input, PaperMaintenanceMarketSnapshot market) {
        if (input == null || market == null || input.workspaceId() <= 0 || input.accountId() <= 0
                || input.positionId() <= 0 || input.instrumentId() <= 0 || input.sourceId() <= 0
                || input.dataAsOf() == null || market.markPrice() == null || market.markPrice().signum() <= 0
                || market.marketTime() == null || market.marketTime().isAfter(input.dataAsOf())
                || market.marketRevision() < 0 || market.priceStep() == null || market.priceStep().signum() <= 0
                || market.quantityStep() == null || market.quantityStep().signum() <= 0
                || market.contractMultiplier() == null || market.contractMultiplier().signum() <= 0
                || market.takerFeeRate() == null || market.takerFeeRate().signum() < 0
                || market.slippageBps() == null || market.slippageBps().signum() < 0
                || market.maintenanceMarginRate() == null || market.maintenanceMarginRate().signum() < 0
                || market.maintenanceMarginRate().compareTo(BigDecimal.ONE) >= 0
                || market.tierObservedAt() == null) {
            throw new InvestmentException(
                    InvestmentErrorCode.DATA_UNAVAILABLE, "Complete margin-check market facts are unavailable");
        }
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Margin-check scope was not found");
    }
}
