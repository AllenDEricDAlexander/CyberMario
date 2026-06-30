package top.egon.mario.nutrition.service.access;

import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;

/**
 * Applies family-scoped nutrition data authorization.
 */
public interface NutritionAccessService {

    boolean canReadFamily(Long userId, Long familyId);

    void requireReadFamily(Long userId, Long familyId);

    void requireManageFamily(Long userId, Long familyId);

    void requireCookFamily(Long userId, Long familyId);

    void requireConfirmMemberProfile(Long userId, Long familyId, Long memberProfileId);

    boolean canReadFamilyScope(Long userId, Long familyId, NutritionGrantDataScope scope);
}
