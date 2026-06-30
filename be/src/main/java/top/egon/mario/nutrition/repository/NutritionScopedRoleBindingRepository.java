package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionScopedRoleBindingPo;
import top.egon.mario.nutrition.po.enums.NutritionRoleCode;
import top.egon.mario.nutrition.po.enums.NutritionScopeType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.enums.NutritionSubjectType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NutritionScopedRoleBindingRepository extends JpaRepository<NutritionScopedRoleBindingPo, Long> {

    boolean existsBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndScopeIdAndStatusAndDeletedFalse(
            NutritionSubjectType subjectType, Long subjectId, Collection<NutritionRoleCode> roleCodes,
            NutritionScopeType scopeType, Long scopeId, NutritionStatus status);

    List<NutritionScopedRoleBindingPo> findBySubjectTypeAndSubjectIdAndRoleCodeInAndScopeTypeAndStatusAndDeletedFalse(
            NutritionSubjectType subjectType, Long subjectId, Collection<NutritionRoleCode> roleCodes,
            NutritionScopeType scopeType, NutritionStatus status);

    Optional<NutritionScopedRoleBindingPo> findBySubjectTypeAndSubjectIdAndRoleCodeAndScopeTypeAndScopeId(
            NutritionSubjectType subjectType, Long subjectId, NutritionRoleCode roleCode,
            NutritionScopeType scopeType, Long scopeId);
}
