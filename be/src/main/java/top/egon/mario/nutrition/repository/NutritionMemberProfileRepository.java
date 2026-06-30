package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionMemberProfilePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.Optional;

public interface NutritionMemberProfileRepository extends JpaRepository<NutritionMemberProfilePo, Long> {

    Optional<NutritionMemberProfilePo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    Optional<NutritionMemberProfilePo> findByIdAndFamilyIdAndStatusAndDeletedFalse(
            Long id, Long familyId, NutritionStatus status);
}
