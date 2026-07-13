package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionShoppingListPo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionShoppingListRepository extends JpaRepository<NutritionShoppingListPo, Long> {

    Optional<NutritionShoppingListPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    List<NutritionShoppingListPo> findByFamilyIdAndDeletedFalseOrderByListDateDescIdDesc(Long familyId);

    List<NutritionShoppingListPo> findByFamilyIdAndMealPlanIdAndDeletedFalseOrderByListDateDescIdDesc(
            Long familyId, Long mealPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shoppingList from NutritionShoppingListPo shoppingList "
            + "where shoppingList.familyId = :familyId "
            + "and shoppingList.mealPlanId = :mealPlanId "
            + "and shoppingList.deleted = false "
            + "order by shoppingList.id asc")
    List<NutritionShoppingListPo> findLockedByFamilyIdAndMealPlanIdAndDeletedFalseOrderByIdAsc(
            @Param("familyId") Long familyId, @Param("mealPlanId") Long mealPlanId);

    List<NutritionShoppingListPo> findByFamilyIdAndMealPlanIdInAndDeletedFalseOrderByListDateAscIdAsc(
            Long familyId, Collection<Long> mealPlanIds);

    List<NutritionShoppingListPo> findByFamilyIdAndListDateBetweenAndDeletedFalseOrderByListDateAscIdAsc(
            Long familyId, LocalDate periodStart, LocalDate periodEnd);
}
