package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionBudgetSnapshotPo;

public interface NutritionBudgetSnapshotRepository extends JpaRepository<NutritionBudgetSnapshotPo, Long> {
}
