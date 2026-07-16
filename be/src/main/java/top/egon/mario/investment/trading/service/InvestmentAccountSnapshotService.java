package top.egon.mario.investment.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotId;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotPo;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;
import top.egon.mario.investment.portfolio.repository.InvestmentAccountSnapshotRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.portfolio.repository.InvestmentPositionRepository;
import top.egon.mario.investment.trading.service.model.AccountSnapshotResult;
import top.egon.mario.investment.trading.service.model.PositionMarkSnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Persists exact account equity facts from a caller-frozen mark snapshot. */
@Service
public class InvestmentAccountSnapshotService {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private final InvestmentPaperAccountRepository accountRepository;
    private final InvestmentPositionRepository positionRepository;
    private final InvestmentAccountSnapshotRepository snapshotRepository;

    public InvestmentAccountSnapshotService(
            InvestmentPaperAccountRepository accountRepository,
            InvestmentPositionRepository positionRepository,
            InvestmentAccountSnapshotRepository snapshotRepository) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public AccountSnapshotResult capture(
            long workspaceId, long accountId, Instant snapshotTime,
            Map<Long, PositionMarkSnapshot> marks) {
        InvestmentPaperAccountPo account = accountRepository
                .findByIdAndWorkspaceIdForUpdate(accountId, workspaceId)
                .orElseThrow(InvestmentAccountSnapshotService::notFound);
        List<InvestmentPositionPo> positions = positionRepository.findByAccountIdForUpdate(accountId);
        BigDecimal usedMargin = sum(positions, InvestmentPositionPo::getIsolatedMargin);
        BigDecimal maintenance = sum(positions, InvestmentPositionPo::getMaintenanceMargin);
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        for (InvestmentPositionPo position : positions) {
            PositionMarkSnapshot mark = marks.get(position.getInstrumentId());
            if (mark == null || mark.markPrice() == null || mark.markPrice().signum() <= 0
                    || mark.contractMultiplier() == null || mark.contractMultiplier().signum() <= 0) {
                throw new InvestmentException(
                        InvestmentErrorCode.DATA_UNAVAILABLE, "Complete account mark snapshot is unavailable");
            }
            BigDecimal notional = position.getQuantity().multiply(mark.markPrice())
                    .multiply(mark.contractMultiplier()).abs();
            gross = gross.add(notional);
            BigDecimal priceChange = "LONG".equals(position.getPositionSide())
                    ? mark.markPrice().subtract(position.getEntryPrice())
                    : position.getEntryPrice().subtract(mark.markPrice());
            unrealized = unrealized.add(priceChange.multiply(position.getQuantity())
                    .multiply(mark.contractMultiplier()));
        }
        BigDecimal equity = account.getWalletBalance().add(unrealized);
        BigDecimal available = equity.subtract(usedMargin);
        BigDecimal totalReturn = equity.subtract(account.getInitialEquity())
                .divide(account.getInitialEquity(), MATH);
        BigDecimal previousPeak = snapshotRepository
                .findFirstByIdAccountIdOrderByEquityDescIdSnapshotTimeAsc(accountId)
                .map(InvestmentAccountSnapshotPo::getEquity).orElse(account.getInitialEquity());
        BigDecimal peak = previousPeak.max(account.getInitialEquity()).max(equity);
        BigDecimal drawdown = peak.signum() <= 0 ? BigDecimal.ZERO
                : peak.subtract(equity).max(BigDecimal.ZERO).divide(peak, MATH).min(BigDecimal.ONE);

        InvestmentAccountSnapshotPo snapshot = new InvestmentAccountSnapshotPo();
        snapshot.setId(new InvestmentAccountSnapshotId(accountId, snapshotTime));
        snapshot.setWalletBalance(account.getWalletBalance());
        snapshot.setUsedMargin(usedMargin);
        snapshot.setMaintenanceMargin(maintenance);
        snapshot.setUnrealizedPnl(unrealized);
        snapshot.setEquity(equity);
        snapshot.setAvailableBalance(available);
        snapshot.setGrossExposure(gross);
        snapshot.setTotalReturn(totalReturn);
        snapshot.setDrawdown(drawdown);
        snapshot.setPositionCount((long) positions.size());
        snapshot.setCreatedAt(snapshotTime);
        snapshotRepository.saveAndFlush(snapshot);
        return new AccountSnapshotResult(
                accountId, snapshotTime, account.getWalletBalance(), usedMargin, maintenance,
                unrealized, equity, available, gross, totalReturn, drawdown, positions.size());
    }

    private static BigDecimal sum(
            List<InvestmentPositionPo> positions,
            java.util.function.Function<InvestmentPositionPo, BigDecimal> value) {
        return positions.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static InvestmentException notFound() {
        return new InvestmentException(InvestmentErrorCode.NOT_FOUND, "Paper account snapshot scope was not found");
    }
}
