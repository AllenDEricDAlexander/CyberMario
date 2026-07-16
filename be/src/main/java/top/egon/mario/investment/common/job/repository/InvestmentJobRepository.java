package top.egon.mario.investment.common.job.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.common.job.po.InvestmentJobPo;

import java.util.Optional;

public interface InvestmentJobRepository extends JpaRepository<InvestmentJobPo, Long> {

    Optional<InvestmentJobPo> findByIdempotencyKey(String idempotencyKey);
}
