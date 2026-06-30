package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;

public interface NutritionShoppingListRepository extends JpaRepository<NutritionShoppingListPo, Long> {
}
