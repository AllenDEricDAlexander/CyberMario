package top.egon.mario.nutrition.service.rule;

import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record MemberRuleProfile(
        Long memberProfileId,
        NutritionMemberType memberType,
        List<String> allergyTags,
        List<String> dislikeTags,
        List<String> dietGoals,
        List<String> restrictionTags,
        BigDecimal targetSodium
) {

    public MemberRuleProfile {
        allergyTags = normalizeList(allergyTags);
        dislikeTags = normalizeList(dislikeTags);
        dietGoals = normalizeList(dietGoals).stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        restrictionTags = normalizeList(restrictionTags);
    }

    public MemberRuleProfile(Long memberProfileId, List<String> allergyTags, List<String> dislikeTags,
                             List<String> dietGoals, BigDecimal targetSodium) {
        this(memberProfileId, NutritionMemberType.ADULT, allergyTags, dislikeTags,
                dietGoals, List.of(), targetSodium);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }
}
