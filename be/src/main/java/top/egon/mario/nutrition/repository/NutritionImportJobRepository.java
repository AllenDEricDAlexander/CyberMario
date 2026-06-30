package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionImportJobPo;

public interface NutritionImportJobRepository extends JpaRepository<NutritionImportJobPo, Long> {
}
