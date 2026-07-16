package top.egon.mario.investment.portfolio.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.portfolio.po.InvestmentPositionPo;

import java.util.List;
import java.util.Optional;

public interface InvestmentPositionRepository extends JpaRepository<InvestmentPositionPo, Long> {

    Optional<InvestmentPositionPo> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select position from InvestmentPositionPo position
            where position.accountId = :accountId
            order by position.instrumentId asc
            """)
    List<InvestmentPositionPo> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    List<InvestmentPositionPo> findByAccountIdOrderByInstrumentIdAsc(Long accountId);

    List<InvestmentPositionPo> findByInstrumentIdOrderByAccountIdAscInstrumentIdAsc(Long instrumentId);

    Optional<InvestmentPositionPo> findByIdAndAccountIdAndInstrumentId(
            Long id, Long accountId, Long instrumentId);
}
