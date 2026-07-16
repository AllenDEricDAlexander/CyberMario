package top.egon.mario.investment.trading.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.investment.trading.po.InvestmentPaperOrderPo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvestmentPaperOrderRepository extends JpaRepository<InvestmentPaperOrderPo, Long> {

    Optional<InvestmentPaperOrderPo> findByIntentId(Long intentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select paperOrder from InvestmentPaperOrderPo paperOrder
            where paperOrder.accountId = :accountId
              and paperOrder.status = 'PENDING_MATCH'
              and paperOrder.positionAction = 'OPEN'
              and paperOrder.deleted = false
            order by paperOrder.id asc
            """)
    List<InvestmentPaperOrderPo> findPendingOpeningByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select paperOrder from InvestmentPaperOrderPo paperOrder
            where paperOrder.id = :orderId
              and paperOrder.accountId = :accountId
              and paperOrder.instrumentId = :instrumentId
              and paperOrder.deleted = false
            """)
    Optional<InvestmentPaperOrderPo> findByScopeForUpdate(
            @Param("orderId") Long orderId,
            @Param("accountId") Long accountId,
            @Param("instrumentId") Long instrumentId);

    long countByAccountIdAndSubmittedAtGreaterThanEqualAndDeletedFalse(Long accountId, Instant since);

    Optional<InvestmentPaperOrderPo> findFirstByAccountIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(Long accountId);

    Page<InvestmentPaperOrderPo> findByAccountIdAndDeletedFalse(Long accountId, Pageable pageable);
}
