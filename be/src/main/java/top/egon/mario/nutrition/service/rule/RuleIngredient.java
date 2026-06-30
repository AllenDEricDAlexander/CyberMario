package top.egon.mario.nutrition.service.rule;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

public record RuleIngredient(String rawFoodName, List<String> tags) {

    public RuleIngredient {
        rawFoodName = StringUtils.hasText(rawFoodName) ? rawFoodName.trim() : "";
        tags = tags == null ? List.of() : tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    public boolean matches(String tag) {
        String normalizedTag = normalize(tag);
        if (!StringUtils.hasText(normalizedTag)) {
            return false;
        }
        return normalize(rawFoodName).contains(normalizedTag)
                || tags.stream().map(RuleIngredient::normalize).anyMatch(normalizedTag::equals);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }
}
