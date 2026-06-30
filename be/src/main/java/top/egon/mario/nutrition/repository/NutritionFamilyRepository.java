package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionFamilyRepository extends JpaRepository<NutritionFamilyPo, Long> {

    Optional<NutritionFamilyPo> findByIdAndDeletedFalse(Long id);

    List<NutritionFamilyPo> findByOwnerUserIdAndStatusAndDeletedFalse(Long ownerUserId, NutritionStatus status);

    List<NutritionFamilyPo> findByIdInAndStatusAndDeletedFalse(Collection<Long> ids, NutritionStatus status);

    boolean existsByIdAndStatusAndDeletedFalse(Long id, NutritionStatus status);

    boolean existsByIdAndOwnerUserIdAndStatusAndDeletedFalse(Long id, Long ownerUserId, NutritionStatus status);
}
