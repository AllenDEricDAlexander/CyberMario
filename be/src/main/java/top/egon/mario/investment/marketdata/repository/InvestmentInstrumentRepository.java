package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.common.model.ProductType;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentPo;

import java.util.Optional;

/**
 * Persistence for internal futures instruments.
 */
public interface InvestmentInstrumentRepository extends JpaRepository<InvestmentInstrumentPo, Long> {

    Optional<InvestmentInstrumentPo> findByVenueIdAndProductTypeAndSymbolAndDeletedFalse(
            Long venueId, ProductType productType, String symbol);
}
