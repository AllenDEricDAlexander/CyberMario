package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionClanPo;

public interface NutritionClanRepository extends JpaRepository<NutritionClanPo, Long> {
}
