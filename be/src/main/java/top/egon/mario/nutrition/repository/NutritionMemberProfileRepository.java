package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionMemberProfileRepository extends JpaRepository<NutritionMemberProfilePo, Long> {

    Optional<NutritionMemberProfilePo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionMemberProfilePo> findByIdAndFamilyIdAndStatusAndDeletedFalse(
            Long id, Long familyId, NutritionStatus status);

    Optional<NutritionMemberProfilePo> findByFamilyIdAndBoundUserIdAndStatusAndDeletedFalse(
            Long familyId, Long boundUserId, NutritionStatus status);

    List<NutritionMemberProfilePo> findByFamilyIdAndStatusAndDeletedFalse(Long familyId,
                                                                          NutritionStatus status);

    List<NutritionMemberProfilePo> findByFamilyIdAndDeletedFalseOrderByIdAsc(Long familyId);

    boolean existsByFamilyIdAndBoundUserIdAndIdNotAndStatusAndDeletedFalse(
            Long familyId, Long boundUserId, Long id, NutritionStatus status);
}
