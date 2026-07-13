package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionDataGrantPo;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionDataGrantRepository extends JpaRepository<NutritionDataGrantPo, Long> {

    @Query("""
            select count(grant) > 0
            from NutritionDataGrantPo grant
            where grant.familyId = :familyId
              and grant.granteeType = :granteeType
              and grant.granteeId = :granteeId
              and grant.dataScope = :dataScope
              and grant.memberProfileId is null
              and grant.permissionLevel in :permissionLevels
              and grant.status = :status
              and grant.deleted = false
              and (grant.expiresAt is null or grant.expiresAt > :now)
            """)
    boolean existsReadableGrant(@Param("familyId") Long familyId,
                                @Param("granteeType") String granteeType,
                                @Param("granteeId") Long granteeId,
                                @Param("dataScope") NutritionGrantDataScope dataScope,
                                @Param("permissionLevels") Collection<NutritionGrantPermissionLevel> permissionLevels,
                                @Param("status") NutritionStatus status,
                                @Param("now") Instant now);

    @Query("""
            select count(grant) > 0
            from NutritionDataGrantPo grant
            where grant.familyId = :familyId
              and (grant.memberProfileId is null or grant.memberProfileId = :memberProfileId)
              and grant.granteeType = :granteeType
              and grant.granteeId = :granteeId
              and grant.dataScope = :dataScope
              and grant.permissionLevel in :permissionLevels
              and grant.status = :status
              and grant.deleted = false
              and (grant.expiresAt is null or grant.expiresAt > :now)
            """)
    boolean existsMemberGrant(@Param("familyId") Long familyId,
                              @Param("memberProfileId") Long memberProfileId,
                              @Param("granteeType") String granteeType,
                              @Param("granteeId") Long granteeId,
                              @Param("dataScope") NutritionGrantDataScope dataScope,
                              @Param("permissionLevels") Collection<NutritionGrantPermissionLevel> permissionLevels,
                              @Param("status") NutritionStatus status,
                              @Param("now") Instant now);

    List<NutritionDataGrantPo> findByFamilyIdAndDeletedFalseOrderByIdAsc(Long familyId);

    Optional<NutritionDataGrantPo> findByIdAndFamilyIdAndDeletedFalse(Long id, Long familyId);

    List<NutritionDataGrantPo> findByFamilyIdAndGranteeTypeAndGranteeIdAndDataScopeOrderByIdAsc(
            Long familyId, String granteeType, Long granteeId, NutritionGrantDataScope dataScope);
}
