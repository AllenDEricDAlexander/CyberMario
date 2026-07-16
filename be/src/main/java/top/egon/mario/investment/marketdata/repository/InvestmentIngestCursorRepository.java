package top.egon.mario.investment.marketdata.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.common.model.BarInterval;
import top.egon.mario.investment.common.model.PriceType;
import top.egon.mario.investment.marketdata.po.InvestmentIngestCursorPo;

import java.util.Optional;

/**
 * Persistence and pessimistic fencing for ingestion cursor dimensions.
 */
public interface InvestmentIngestCursorRepository extends JpaRepository<InvestmentIngestCursorPo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cursor from InvestmentIngestCursorPo cursor
            where cursor.sourceId = :sourceId
              and cursor.instrumentId = :instrumentId
              and cursor.dataType = :dataType
              and cursor.priceType = :priceType
              and cursor.interval = :interval
            """)
    Optional<InvestmentIngestCursorPo> findDimensionForUpdate(
            @Param("sourceId") Long sourceId,
            @Param("instrumentId") Long instrumentId,
            @Param("dataType") String dataType,
            @Param("priceType") PriceType priceType,
            @Param("interval") BarInterval interval);
}
