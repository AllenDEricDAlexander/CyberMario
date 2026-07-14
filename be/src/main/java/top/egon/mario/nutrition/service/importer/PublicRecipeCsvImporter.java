package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV importer for platform-public recipes.
 */
@Component
public class PublicRecipeCsvImporter
        extends NutritionCsvImportTemplate<PublicRecipeCsvImporter.PublicRecipeRow> {

    private final RecipeService recipeService;

    public PublicRecipeCsvImporter(NutritionImportJobRepository importJobRepository,
                                   NutritionImportErrorRepository importErrorRepository,
                                   NutritionImportFailureRecorder failureRecorder,
                                   EntityManager entityManager,
                                   ObjectMapper objectMapper,
                                   RecipeService recipeService) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.recipeService = recipeService;
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.PUBLIC_RECIPE;
    }

    @Override
    protected Class<PublicRecipeRow> rowType() {
        return PublicRecipeRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
        if (request.familyId() != null) {
            throw new NutritionException(
                    "NUTRITION_IMPORT_FAMILY_FORBIDDEN", "public recipe import must not specify familyId");
        }
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
    protected PublicRecipeRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        String recipeName = requireValue(row, issues, "recipe_name");
        String ingredientName = requireValue(row, issues, "ingredient_name");
        BigDecimal amount = decimalValue(row, issues, "amount", true);
        String unit = requireValue(row, issues, "unit");
        String category = trimToNull(value(row, "category"));
        if (issues.hasError()) {
            return null;
        }
        return new PublicRecipeRow(recipeName, ingredientName, amount, unit, category);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<PublicRecipeRow> validRows,
                               RbacPrincipal principal) {
        Map<String, List<PublicRecipeRow>> rowsByRecipeName = new LinkedHashMap<>();
        for (PublicRecipeRow row : validRows) {
            rowsByRecipeName.computeIfAbsent(row.recipeName(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<PublicRecipeRow>> entry : rowsByRecipeName.entrySet()) {
            List<RecipeIngredientRequest> ingredients = entry.getValue().stream()
                    .map(row -> new RecipeIngredientRequest(
                            row.ingredientName(), row.category(), row.amount(), row.unit(), false))
                    .toList();
            recipeService.createPlatformRecipe(new CreateRecipeRequest(
                    entry.getKey(), null, "", 1, ingredients), principal);
        }
    }

    public record PublicRecipeRow(
            String recipeName,
            String ingredientName,
            BigDecimal amount,
            String unit,
            String category
    ) {
    }
}
