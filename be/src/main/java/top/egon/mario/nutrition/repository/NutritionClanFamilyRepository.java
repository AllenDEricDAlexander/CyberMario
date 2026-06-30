package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionClanFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

public interface NutritionClanFamilyRepository extends JpaRepository<NutritionClanFamilyPo, Long> {

    boolean existsByClanIdAndFamilyIdAndRelationStatusAndDeletedFalse(Long clanId, Long familyId,
                                                                      NutritionStatus relationStatus);
}
