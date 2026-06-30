package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionFamilyRepository extends JpaRepository<NutritionFamilyPo, Long> {

    Optional<NutritionFamilyPo> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select family from NutritionFamilyPo family where family.id = :id and family.deleted = false")
    Optional<NutritionFamilyPo> findLockedByIdAndDeletedFalse(@Param("id") Long id);

    List<NutritionFamilyPo> findByOwnerUserIdAndStatusAndDeletedFalse(Long ownerUserId, NutritionStatus status);

    List<NutritionFamilyPo> findByIdInAndStatusAndDeletedFalse(Collection<Long> ids, NutritionStatus status);

    List<NutritionFamilyPo> findByAiEnabledTrueAndAiGenerateTimeLessThanEqualAndStatusAndDeletedFalse(
            LocalTime aiGenerateTime, NutritionStatus status);

    boolean existsByIdAndStatusAndDeletedFalse(Long id, NutritionStatus status);

    boolean existsByIdAndOwnerUserIdAndStatusAndDeletedFalse(Long id, Long ownerUserId, NutritionStatus status);
}
