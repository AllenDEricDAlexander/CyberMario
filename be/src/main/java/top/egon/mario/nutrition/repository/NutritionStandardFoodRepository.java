package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionStandardFoodRepository extends JpaRepository<NutritionStandardFoodPo, Long> {

    List<NutritionStandardFoodPo> findByStatusAndDeletedFalseOrderByIdDesc(NutritionStatus status);

    Optional<NutritionStandardFoodPo> findFirstByNameCnIgnoreCaseAndCategoryIgnoreCaseAndStatusAndDeletedFalseOrderByIdAsc(
            String nameCn, String category, NutritionStatus status);
}
