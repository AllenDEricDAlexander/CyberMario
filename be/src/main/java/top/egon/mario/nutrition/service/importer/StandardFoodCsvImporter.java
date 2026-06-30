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
        return new StandardFoodRow(name, category, calories, protein, fat, carbs);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<StandardFoodRow> validRows, RbacPrincipal principal) {
        for (StandardFoodRow row : validRows) {
            NutritionStandardFoodPo food = new NutritionStandardFoodPo();
            food.setNameCn(row.nameCn());
            food.setCategory(row.category());
            food.setCaloriesPer100g(row.caloriesPer100g());
            food.setProteinPer100g(row.proteinPer100g());
            food.setFatPer100g(row.fatPer100g());
            food.setCarbsPer100g(row.carbsPer100g());
            food.setDataQuality("IMPORTED");
            food.setStatus(NutritionStatus.ACTIVE);
            standardFoodRepository.save(food);
        }
    }

    private String preferredNameColumn(CsvRow row) {
        return row.value("name_cn") != null ? "name_cn" : "name";
    }

    public record StandardFoodRow(
            String nameCn,
            String category,
            BigDecimal caloriesPer100g,
            BigDecimal proteinPer100g,
            BigDecimal fatPer100g,
            BigDecimal carbsPer100g
    ) {
    }
}
