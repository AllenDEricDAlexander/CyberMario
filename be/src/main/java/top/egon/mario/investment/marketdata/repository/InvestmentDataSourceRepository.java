package top.egon.mario.investment.marketdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.marketdata.po.InvestmentDataSourcePo;

import java.util.Optional;

/**
 * Persistence for logical provider data sources.
 */
public interface InvestmentDataSourceRepository extends JpaRepository<InvestmentDataSourcePo, Long> {

    Optional<InvestmentDataSourcePo> findByCodeAndDeletedFalse(String code);
}
