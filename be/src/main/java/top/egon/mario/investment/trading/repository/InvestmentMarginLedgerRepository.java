package top.egon.mario.investment.trading.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.trading.po.InvestmentMarginLedgerPo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface InvestmentMarginLedgerRepository extends JpaRepository<InvestmentMarginLedgerPo, Long> {

    Optional<InvestmentMarginLedgerPo> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select coalesce(sum(-ledger.amount), 0) from InvestmentMarginLedgerPo ledger
            where ledger.accountId = :accountId
              and ledger.eventType = 'REALIZED_PNL'
              and ledger.amount < 0
              and ledger.occurredAt >= :since
            """)
    BigDecimal sumDailyLoss(@Param("accountId") Long accountId, @Param("since") Instant since);
}
