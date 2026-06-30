package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionRecordPo;

public interface NutritionRecordRepository extends JpaRepository<NutritionRecordPo, Long> {
}
