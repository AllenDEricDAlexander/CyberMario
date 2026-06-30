package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionClanFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionClanFamilyRepository extends JpaRepository<NutritionClanFamilyPo, Long> {

    Optional<NutritionClanFamilyPo> findByClanIdAndFamilyId(Long clanId, Long familyId);

    List<NutritionClanFamilyPo> findByFamilyIdAndRelationStatusAndDeletedFalse(Long familyId,
                                                                               NutritionStatus relationStatus);

    boolean existsByClanIdAndFamilyIdAndRelationStatusAndDeletedFalse(Long clanId, Long familyId,
                                                                      NutritionStatus relationStatus);
}
