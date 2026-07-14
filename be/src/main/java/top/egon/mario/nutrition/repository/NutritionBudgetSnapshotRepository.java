package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionBudgetSnapshotPo;

import java.time.LocalDate;
import java.util.List;

public interface NutritionBudgetSnapshotRepository extends JpaRepository<NutritionBudgetSnapshotPo, Long> {

    List<NutritionBudgetSnapshotPo> findByFamilyIdAndPeriodStartAndPeriodEndAndDeletedFalseOrderByIdDesc(
            Long familyId, LocalDate periodStart, LocalDate periodEnd);
}
