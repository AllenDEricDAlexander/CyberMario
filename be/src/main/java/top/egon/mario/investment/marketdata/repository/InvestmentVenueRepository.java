package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentVenuePo;

import java.util.Optional;

/**
 * Persistence for platform trading venues.
 */
public interface InvestmentVenueRepository extends JpaRepository<InvestmentVenuePo, Long> {

    Optional<InvestmentVenuePo> findByCodeAndDeletedFalse(String code);
}
