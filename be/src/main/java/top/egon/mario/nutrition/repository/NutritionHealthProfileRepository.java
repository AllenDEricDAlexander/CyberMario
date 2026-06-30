package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionHealthProfilePo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionHealthProfileRepository extends JpaRepository<NutritionHealthProfilePo, Long> {

    Optional<NutritionHealthProfilePo> findByFamilyIdAndMemberProfileId(Long familyId, Long memberProfileId);

    @Query("""
            select healthProfile
            from NutritionHealthProfilePo healthProfile
            where healthProfile.familyId = :familyId
              and healthProfile.deleted = false
              and exists (
                  select member.id
                  from NutritionMemberProfilePo member
                  where member.id = healthProfile.memberProfileId
                    and member.familyId = :familyId
                    and member.status = :memberStatus
                    and member.deleted = false
              )
            order by healthProfile.id asc
            """)
    List<NutritionHealthProfilePo> findActiveMemberHealthProfiles(@Param("familyId") Long familyId,
                                                                  @Param("memberStatus") NutritionStatus memberStatus);
}
