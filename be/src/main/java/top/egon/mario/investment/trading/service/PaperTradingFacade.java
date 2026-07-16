package top.egon.mario.investment.trading.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;
import top.egon.mario.investment.portfolio.repository.InvestmentPaperAccountRepository;
import top.egon.mario.investment.trading.matching.PaperMarketSnapshotReader;
import top.egon.mario.investment.trading.service.model.PaperAcceptanceMarketSnapshot;
import top.egon.mario.investment.trading.service.model.PaperTradeCommand;
import top.egon.mario.investment.trading.service.model.PaperTradeResult;

import java.time.Clock;

/**
 * Single write facade shared by manual, strategy, and Agent paper-trading callers.
 */
@Service
public class PaperTradingFacade {

    private final InvestmentPaperAccountRepository accountRepository;
    private final PaperOrderService orderService;
    private final PaperMarketSnapshotReader marketSnapshotReader;
    private final PaperIntentAcceptanceTransactionService acceptanceService;
    private final Clock clock;

    public PaperTradingFacade(
            InvestmentPaperAccountRepository accountRepository,
            PaperOrderService orderService,
            PaperMarketSnapshotReader marketSnapshotReader,
            PaperIntentAcceptanceTransactionService acceptanceService,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.orderService = orderService;
        this.marketSnapshotReader = marketSnapshotReader;
        this.acceptanceService = acceptanceService;
        this.clock = clock;
    }

    /**
     * Reads immutable market facts without a transaction, then enters the short locking acceptance transaction.
     */
    @Transactional(propagation = Propagation.NEVER)
    public PaperTradeResult submitIntent(PaperTradeCommand command) {
        if (command.dataAsOf().isAfter(clock.instant())) {
            throw new InvestmentException(
                    InvestmentErrorCode.INVALID_REQUEST, "Paper trade dataAsOf cannot be in the future");
        }
        InvestmentPaperAccountPo account = accountRepository
                .findOwnedAccount(command.accountId(), command.actorId())
                .orElseThrow(() -> new InvestmentException(
                        InvestmentErrorCode.FORBIDDEN, "Paper account access denied"));
        PaperTradeResult existing = orderService
                .findByIdempotencyKey(command.accountId(), command.idempotencyKey()).orElse(null);
        if (existing != null) {
            return existing;
        }
        PaperAcceptanceMarketSnapshot snapshot = marketSnapshotReader.read(
                command.instrumentId(), command.quantity(), command.dataAsOf());
        return acceptanceService.accept(account.getWorkspaceId(), command, snapshot);
    }
}
