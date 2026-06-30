package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;

public interface NutritionHealthProfileRepository extends JpaRepository<NutritionHealthProfilePo, Long> {
}
