package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentInstrumentSourcePo;

import java.util.Optional;

/**
 * Persistence for provider symbol mappings.
 */
public interface InvestmentInstrumentSourceRepository extends JpaRepository<InvestmentInstrumentSourcePo, Long> {

    Optional<InvestmentInstrumentSourcePo> findByInstrumentIdAndSourceIdAndDeletedFalse(
            Long instrumentId, Long sourceId);

    Optional<InvestmentInstrumentSourcePo> findBySourceIdAndExternalProductTypeAndExternalSymbolAndDeletedFalse(
            Long sourceId, String externalProductType, String externalSymbol);
}
