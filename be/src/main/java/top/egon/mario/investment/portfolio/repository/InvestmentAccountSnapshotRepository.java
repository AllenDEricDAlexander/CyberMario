package top.egon.mario.investment.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotId;
import top.egon.mario.investment.portfolio.po.InvestmentAccountSnapshotPo;

import java.util.Optional;

public interface InvestmentAccountSnapshotRepository extends
        JpaRepository<InvestmentAccountSnapshotPo, InvestmentAccountSnapshotId> {

    Optional<InvestmentAccountSnapshotPo> findFirstByIdAccountIdOrderByEquityDescIdSnapshotTimeAsc(Long accountId);
}
