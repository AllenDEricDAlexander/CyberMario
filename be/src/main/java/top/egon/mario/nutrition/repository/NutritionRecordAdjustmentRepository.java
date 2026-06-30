package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecordAdjustmentPo;

public interface NutritionRecordAdjustmentRepository extends JpaRepository<NutritionRecordAdjustmentPo, Long> {
}
