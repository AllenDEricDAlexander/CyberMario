package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMealConfirmationItemPo;

import java.util.Collection;
import java.util.List;

public interface NutritionMealConfirmationItemRepository extends JpaRepository<NutritionMealConfirmationItemPo, Long> {

    List<NutritionMealConfirmationItemPo> findByConfirmationIdAndDeletedFalseOrderByIdAsc(Long confirmationId);

    List<NutritionMealConfirmationItemPo> findByConfirmationIdInAndDeletedFalseOrderByIdAsc(
            Collection<Long> confirmationIds);
}
