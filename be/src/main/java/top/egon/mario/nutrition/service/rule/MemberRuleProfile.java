package top.egon.mario.nutrition.service.rule;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record MemberRuleProfile(
        Long memberProfileId,
        List<String> allergyTags,
        List<String> dislikeTags,
        List<String> dietGoals,
        BigDecimal targetSodium
) {

    public MemberRuleProfile {
        allergyTags = normalizeList(allergyTags);
        dislikeTags = normalizeList(dislikeTags);
        dietGoals = normalizeList(dietGoals).stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
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
