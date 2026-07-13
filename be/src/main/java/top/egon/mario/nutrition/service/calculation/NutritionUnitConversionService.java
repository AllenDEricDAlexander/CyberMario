package top.egon.mario.nutrition.service.calculation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.service.NutritionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Deterministic conversion from recipe ingredient units to grams.
 */
@Service
public class NutritionUnitConversionService {

    public BigDecimal toGrams(BigDecimal amount, String unit, BigDecimal gramsPerUnit) {
        if (amount == null || amount.signum() <= 0 || !StringUtils.hasText(unit)) {
            throw new NutritionException(
                    "NUTRITION_UNIT_INVALID", "nutrition ingredient amount and unit are required");
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "mg" -> amount.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
            case "g" -> amount;
            case "kg" -> amount.multiply(new BigDecimal("1000"));
            default -> {
                if (gramsPerUnit == null || gramsPerUnit.signum() <= 0) {
                    throw new NutritionException(
                            "NUTRITION_UNIT_CONVERSION_MISSING",
                            "nutrition ingredient grams-per-unit conversion is required");
                }
                yield amount.multiply(gramsPerUnit);
            }
        };
    }
}
