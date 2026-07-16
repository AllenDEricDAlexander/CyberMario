package top.egon.mario.investment.quant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.quant.po.InvestmentStrategyReleasePo;

import java.util.List;
import java.util.Optional;

public interface InvestmentStrategyReleaseRepository extends JpaRepository<InvestmentStrategyReleasePo, Long> {

    Optional<InvestmentStrategyReleasePo> findByStrategyCodeAndStrategyVersion(
            String strategyCode, String strategyVersion);

    List<InvestmentStrategyReleasePo> findAllByActiveTrue();
}
