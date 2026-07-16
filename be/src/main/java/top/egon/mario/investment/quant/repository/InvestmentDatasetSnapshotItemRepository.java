package top.egon.mario.investment.quant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotItemPo;

import java.time.Instant;
import java.util.List;

public interface InvestmentDatasetSnapshotItemRepository
        extends JpaRepository<InvestmentDatasetSnapshotItemPo, Long> {

    List<InvestmentDatasetSnapshotItemPo> findBySnapshotIdOrderByInstrumentIdAscDataTypeAscPriceTypeAscIntervalCodeAsc(
            Long snapshotId);

    @Query("""
            select (count(item) > 0)
            from InvestmentDatasetSnapshotItemPo item, InvestmentDatasetSnapshotPo snapshot
            where item.snapshotId = snapshot.id
              and snapshot.sourceId = :sourceId
              and item.instrumentId = :instrumentId
              and item.dataType = 'BAR_INTRADAY'
              and item.priceType = :priceType
              and item.intervalCode = :intervalCode
              and item.firstTime < :toExclusive
              and item.lastTime >= :fromInclusive
              and (snapshot.qualityStatus = 'PENDING'
                   or (snapshot.qualityStatus = 'VERIFIED'
                       and (snapshot.artifactUri is null or snapshot.artifactUri = '')))
            """)
    boolean existsProtectedIntradayRange(@Param("sourceId") Long sourceId,
                                         @Param("instrumentId") Long instrumentId,
                                         @Param("priceType") String priceType,
                                         @Param("intervalCode") String intervalCode,
                                         @Param("fromInclusive") Instant fromInclusive,
                                         @Param("toExclusive") Instant toExclusive);
}
