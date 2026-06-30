package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionShoppingListItemPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionShoppingListItemRepository extends JpaRepository<NutritionShoppingListItemPo, Long> {

    Optional<NutritionShoppingListItemPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionShoppingListItemPo> findByIdAndFamilyIdAndShoppingListIdAndDeletedFalse(
            Long id, Long familyId, Long shoppingListId);

    List<NutritionShoppingListItemPo> findByShoppingListIdAndDeletedFalseOrderByIdAsc(Long shoppingListId);

    List<NutritionShoppingListItemPo> findByShoppingListIdInAndDeletedFalseOrderByIdAsc(
            Collection<Long> shoppingListIds);
}
