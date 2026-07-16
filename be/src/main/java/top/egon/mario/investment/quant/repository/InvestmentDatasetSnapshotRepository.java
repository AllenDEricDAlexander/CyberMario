package top.egon.mario.investment.quant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.investment.quant.po.InvestmentDatasetSnapshotPo;

import java.util.Optional;

public interface InvestmentDatasetSnapshotRepository extends JpaRepository<InvestmentDatasetSnapshotPo, Long> {

    Optional<InvestmentDatasetSnapshotPo> findByWorkspaceIdAndDatasetHash(Long workspaceId, String datasetHash);
}
