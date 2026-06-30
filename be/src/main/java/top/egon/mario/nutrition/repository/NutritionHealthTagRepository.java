package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionHealthTagPo;

public interface NutritionHealthTagRepository extends JpaRepository<NutritionHealthTagPo, Long> {
}
