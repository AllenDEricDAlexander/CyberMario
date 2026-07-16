package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentPositionTierPo;

import java.time.Instant;
import java.util.List;

/**
 * Persistence for immutable provider position-tier snapshots.
 */
public interface InvestmentPositionTierRepository extends JpaRepository<InvestmentPositionTierPo, Long> {

    List<InvestmentPositionTierPo> findBySourceIdAndInstrumentIdAndObservedAtOrderByTierLevel(
            Long sourceId, Long instrumentId, Instant observedAt);
}
