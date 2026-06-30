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
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV importer for private family recipes.
 */
@Component
public class FamilyRecipeCsvImporter extends NutritionCsvImportTemplate<FamilyRecipeCsvImporter.FamilyRecipeRow> {

    private final NutritionAccessService accessService;
    private final RecipeService recipeService;

    public FamilyRecipeCsvImporter(NutritionImportJobRepository importJobRepository,
                                   NutritionImportErrorRepository importErrorRepository,
                                   NutritionImportFailureRecorder failureRecorder,
                                   EntityManager entityManager,
                                   ObjectMapper objectMapper,
                                   NutritionAccessService accessService,
                                   RecipeService recipeService) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.accessService = accessService;
        this.recipeService = recipeService;
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.PRIVATE_RECIPE;
    }

    @Override
    protected Class<FamilyRecipeRow> rowType() {
        return FamilyRecipeRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        Long familyId = requireFamilyId(request.familyId());
        accessService.requireManageFamily(actorId(principal), familyId);
    }

    @Override
    protected void authorizeRead(NutritionImportJobPo job, RbacPrincipal principal) {
        accessService.requireReadFamily(actorId(principal), requireFamilyId(job.getFamilyId()));
    }

    @Override
    protected void authorizeConfirm(NutritionImportJobPo job, RbacPrincipal principal) {
        accessService.requireManageFamily(actorId(principal), requireFamilyId(job.getFamilyId()));
    }

    @Override
    protected FamilyRecipeRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        String recipeName = requireValue(row, issues, "recipe_name");
        String ingredientName = requireValue(row, issues, "ingredient_name");
        BigDecimal amount = decimalValue(row, issues, "amount", true);
        String unit = requireValue(row, issues, "unit");
        String category = trimToNull(value(row, "category"));
        if (issues.hasError()) {
            return null;
        }
        return new FamilyRecipeRow(recipeName, ingredientName, amount, unit, category);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<FamilyRecipeRow> validRows, RbacPrincipal principal) {
        Map<String, List<FamilyRecipeRow>> rowsByRecipeName = new LinkedHashMap<>();
        for (FamilyRecipeRow row : validRows) {
            rowsByRecipeName.computeIfAbsent(row.recipeName(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<FamilyRecipeRow>> entry : rowsByRecipeName.entrySet()) {
            List<RecipeIngredientRequest> ingredients = entry.getValue().stream()
                    .map(row -> new RecipeIngredientRequest(
                            row.ingredientName(), row.category(), row.amount(), row.unit(), false))
                    .toList();
            recipeService.createFamilyRecipe(job.getFamilyId(), new CreateRecipeRequest(
                    entry.getKey(), null, "", 1, ingredients), actorId(principal));
        }
    }

    private Long requireFamilyId(Long familyId) {
        if (familyId == null || familyId <= 0) {
            throw new NutritionException("NUTRITION_IMPORT_FAMILY_REQUIRED",
                    "nutrition private recipe import requires familyId");
        }
        return familyId;
    }

    private Long actorId(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return principal.userId();
    }

    public record FamilyRecipeRow(
            String recipeName,
            String ingredientName,
            BigDecimal amount,
            String unit,
            String category
    ) {
    }
}
