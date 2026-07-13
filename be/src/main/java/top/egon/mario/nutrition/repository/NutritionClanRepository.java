package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionClanPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionClanRepository extends JpaRepository<NutritionClanPo, Long> {

    Optional<NutritionClanPo> findByIdAndDeletedFalse(Long id);

    List<NutritionClanPo> findByOwnerUserIdAndStatusAndDeletedFalse(Long ownerUserId, NutritionStatus status);

    List<NutritionClanPo> findByIdInAndStatusAndDeletedFalse(Collection<Long> ids, NutritionStatus status);

    boolean existsByIdAndOwnerUserIdAndStatusAndDeletedFalse(Long id, Long ownerUserId, NutritionStatus status);

    boolean existsByIdAndStatusAndDeletedFalse(Long id, NutritionStatus status);
}
