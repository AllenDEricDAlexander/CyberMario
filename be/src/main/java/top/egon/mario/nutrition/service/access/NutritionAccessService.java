package top.egon.mario.nutrition.service.access;

import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;

/**
 * Applies family-scoped nutrition data authorization.
 */
public interface NutritionAccessService {

    boolean canReadFamily(Long userId, Long familyId);

    boolean canReadFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);

    boolean canWriteFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);

    boolean canManageFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);

    void requireReadFamily(Long userId, Long familyId);

    void requireWriteFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);

    void requireManageFamily(Long userId, Long familyId);

    void requireManageFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);

    void requireCookFamily(Long userId, Long familyId);

    void requireConfirmMemberProfile(Long userId, Long familyId, Long memberProfileId);

    void requireWriteMemberProfile(Long userId, Long familyId, Long memberProfileId);
}
