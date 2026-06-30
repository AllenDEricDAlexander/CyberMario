package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;

public interface NutritionShoppingListItemRepository extends JpaRepository<NutritionShoppingListItemPo, Long> {
}
