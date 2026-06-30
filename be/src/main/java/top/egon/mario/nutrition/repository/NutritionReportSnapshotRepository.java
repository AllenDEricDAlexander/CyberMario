package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionReportSnapshotPo;

public interface NutritionReportSnapshotRepository extends JpaRepository<NutritionReportSnapshotPo, Long> {
}
