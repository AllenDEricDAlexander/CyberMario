package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSV importer for platform standard foods.
 */
@Component
public class StandardFoodCsvImporter extends NutritionCsvImportTemplate<StandardFoodCsvImporter.StandardFoodRow> {

    private static final String DUPLICATE_KEYS = "standardFoodDuplicateKeys";

    private final NutritionStandardFoodRepository standardFoodRepository;

    public StandardFoodCsvImporter(NutritionImportJobRepository importJobRepository,
                                   NutritionImportErrorRepository importErrorRepository,
                                   NutritionImportFailureRecorder failureRecorder,
                                   EntityManager entityManager,
                                   ObjectMapper objectMapper,
                                   NutritionStandardFoodRepository standardFoodRepository) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.standardFoodRepository = standardFoodRepository;
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.STANDARD_FOOD;
    }

    @Override
    protected Class<StandardFoodRow> rowType() {
        return StandardFoodRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected void authorizeRead(NutritionImportJobPo job, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected void authorizeConfirm(NutritionImportJobPo job, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
    }

    @Override
    protected StandardFoodRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        String name = requireFirstValue(row, issues, preferredNameColumn(row), "name_cn", "name");
        String category = requireValue(row, issues, "category");
        BigDecimal calories = decimalValue(row, issues, "calories_per_100g", false);
        BigDecimal protein = decimalValue(row, issues, "protein_per_100g", false);
        BigDecimal fat = decimalValue(row, issues, "fat_per_100g", false);
        BigDecimal carbs = decimalValue(row, issues, "carbs_per_100g", false);
        BigDecimal sugar = decimalValue(row, issues, "sugar_per_100g", false);
        BigDecimal sodium = decimalValue(row, issues, "sodium_per_100g", false);
        BigDecimal fiber = decimalValue(row, issues, "fiber_per_100g", false);
        BigDecimal cholesterol = decimalValue(row, issues, "cholesterol_per_100g", false);
        BigDecimal giValue = decimalValue(row, issues, "gi_value", false);
        NutritionStatus status = statusValue(row, issues);
        if (issues.hasError()) {
            return null;
        }
        if (StringUtils.hasText(name) && StringUtils.hasText(category)) {
            Set<String> duplicateKeys = context.computeIfAbsent(DUPLICATE_KEYS, LinkedHashSet::new);
            String key = name.trim().toLowerCase(Locale.ROOT) + "|" + category.trim().toLowerCase(Locale.ROOT);
            if (!duplicateKeys.add(key)) {
                issues.warning(preferredNameColumn(row), "DUPLICATE_NAME_IN_CATEGORY",
                        "standard food name is duplicated within category");
                return null;
            }
        }
        return new StandardFoodRow(name, trimToNull(value(row, "name_en")), listValue(row, "aliases"), category,
                trimToNull(value(row, "external_source")), trimToNull(value(row, "external_food_id")),
                calories, protein, fat, carbs, sugar, sodium, fiber, cholesterol,
                trimToNull(value(row, "purine_level")), giValue, listValue(row, "allergen_tags"),
                listValue(row, "suitable_tags"), defaultValue(row, "data_quality", "IMPORTED"), status);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<StandardFoodRow> validRows, RbacPrincipal principal) {
        for (StandardFoodRow row : validRows) {
            NutritionStandardFoodPo food = new NutritionStandardFoodPo();
            food.setNameCn(row.nameCn());
            food.setNameEn(row.nameEn());
            food.setAliases(writeList(row.aliases()));
            food.setCategory(row.category());
            food.setExternalSource(row.externalSource());
            food.setExternalFoodId(row.externalFoodId());
            food.setCaloriesPer100g(row.caloriesPer100g());
            food.setProteinPer100g(row.proteinPer100g());
            food.setFatPer100g(row.fatPer100g());
            food.setCarbsPer100g(row.carbsPer100g());
            food.setSugarPer100g(row.sugarPer100g());
            food.setSodiumPer100g(row.sodiumPer100g());
            food.setFiberPer100g(row.fiberPer100g());
            food.setCholesterolPer100g(row.cholesterolPer100g());
            food.setPurineLevel(row.purineLevel());
            food.setGiValue(row.giValue());
            food.setAllergenTags(writeList(row.allergenTags()));
            food.setSuitableTags(writeList(row.suitableTags()));
            food.setDataQuality(row.dataQuality());
            food.setStatus(row.status());
            standardFoodRepository.save(food);
        }
    }

    private String preferredNameColumn(CsvRow row) {
        return row.value("name_cn") != null ? "name_cn" : "name";
    }

    private NutritionStatus statusValue(CsvRow row, IssueCollector issues) {
        String raw = value(row, "status");
        if (!StringUtils.hasText(raw)) {
            return NutritionStatus.ACTIVE;
        }
        try {
            return NutritionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            issues.error("status", "INVALID_STATUS", "status must be ACTIVE, DISABLED or ARCHIVED");
            return null;
        }
    }

    private List<String> listValue(CsvRow row, String columnName) {
        String raw = value(row, columnName);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split("[|;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String defaultValue(CsvRow row, String columnName, String defaultValue) {
        String raw = trimToNull(value(row, columnName));
        return raw == null ? defaultValue : raw;
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper().writeValueAsString(values);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize standard food tags", ex);
        }
    }

    public record StandardFoodRow(
            String nameCn,
            String nameEn,
            List<String> aliases,
            String category,
            String externalSource,
            String externalFoodId,
            BigDecimal caloriesPer100g,
            BigDecimal proteinPer100g,
            BigDecimal fatPer100g,
            BigDecimal carbsPer100g,
            BigDecimal sugarPer100g,
            BigDecimal sodiumPer100g,
            BigDecimal fiberPer100g,
            BigDecimal cholesterolPer100g,
            String purineLevel,
            BigDecimal giValue,
            List<String> allergenTags,
            List<String> suitableTags,
            String dataQuality,
            NutritionStatus status
    ) {
    }
}
