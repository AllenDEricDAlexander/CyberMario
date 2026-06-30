package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionImportErrorPo;

public interface NutritionImportErrorRepository extends JpaRepository<NutritionImportErrorPo, Long> {
}
