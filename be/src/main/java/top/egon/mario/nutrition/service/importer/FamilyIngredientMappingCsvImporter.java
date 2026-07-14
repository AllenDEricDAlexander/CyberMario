package top.egon.mario.nutrition.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.po.NutritionImportJobPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * CSV importer for family-owned recipe ingredient mappings.
 */
@Component
public class FamilyIngredientMappingCsvImporter
        extends NutritionCsvImportTemplate<FamilyIngredientMappingCsvImporter.MappingRow> {

    private final NutritionAccessService accessService;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionStandardFoodRepository standardFoodRepository;

    public FamilyIngredientMappingCsvImporter(NutritionImportJobRepository importJobRepository,
                                              NutritionImportErrorRepository importErrorRepository,
                                              NutritionImportFailureRecorder failureRecorder,
                                              EntityManager entityManager,
                                              ObjectMapper objectMapper,
                                              NutritionAccessService accessService,
                                              NutritionRecipeIngredientRepository recipeIngredientRepository,
                                              NutritionStandardFoodRepository standardFoodRepository) {
        super(importJobRepository, importErrorRepository, failureRecorder, entityManager, objectMapper);
        this.accessService = accessService;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.standardFoodRepository = standardFoodRepository;
    }

    @Override
    protected NutritionImportType supportedImportType() {
        return NutritionImportType.FAMILY_INGREDIENT_MAPPING;
    }

    @Override
    protected Class<MappingRow> rowType() {
        return MappingRow.class;
    }

    @Override
    protected void authorizeCreate(CreateNutritionImportJobRequest request, RbacPrincipal principal) {
        accessService.requireManageFamily(actorId(principal), requireFamilyId(request.familyId()));
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
    protected MappingRow mapRow(CsvRow row, ImportContext context, IssueCollector issues) {
        Long recipeIngredientId = longValue(row, issues, "recipe_ingredient_id");
        Long standardFoodId = longValue(row, issues, "standard_food_id");
        return issues.hasError() ? null : new MappingRow(recipeIngredientId, standardFoodId);
    }

    @Override
    protected void persistRows(NutritionImportJobPo job, List<MappingRow> validRows, RbacPrincipal principal) {
        Long familyId = requireFamilyId(job.getFamilyId());
        for (MappingRow row : validRows) {
            NutritionRecipeIngredientPo ingredient = recipeIngredientRepository
                    .findByIdAndDeletedFalse(row.recipeIngredientId())
                    .filter(candidate -> familyId.equals(candidate.getFamilyId()))
                    .orElseThrow(() -> new NutritionException(
                            "NUTRITION_IMPORT_SCOPE_INVALID",
                            "recipe ingredient does not belong to the import family"));
            standardFoodRepository.findByIdAndDeletedFalse(row.standardFoodId())
                    .filter(food -> NutritionStatus.ACTIVE == food.getStatus())
                    .orElseThrow(() -> new NutritionException(
                            "NUTRITION_STANDARD_FOOD_NOT_FOUND", "nutrition standard food not found"));
            ingredient.setStandardFoodId(row.standardFoodId());
            ingredient.setMappingStatus(RecipeService.MAPPING_STATUS_MAPPED);
            recipeIngredientRepository.save(ingredient);
        }
    }

    private Long longValue(CsvRow row, IssueCollector issues, String columnName) {
        String raw = requireValue(row, issues, columnName);
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException ex) {
            issues.error(columnName, "INVALID_ID", columnName + " must be a positive integer");
            return null;
        }
    }

    private Long requireFamilyId(Long familyId) {
        if (familyId == null || familyId <= 0) {
            throw new NutritionException(
                    "NUTRITION_IMPORT_FAMILY_REQUIRED", "family ingredient mapping import requires familyId");
        }
        return familyId;
    }

    private Long actorId(RbacPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId() <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return principal.userId();
    }

    public record MappingRow(Long recipeIngredientId, Long standardFoodId) {
    }
}
