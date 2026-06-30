package top.egon.mario.nutrition.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.CreateStandardFoodRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.response.RecipeIngredientResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.dto.response.StandardFoodResponse;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application service for platform standard foods and family recipes.
 */
@Service
@RequiredArgsConstructor
@Validated
public class RecipeService {

    public static final String MAPPING_STATUS_MAPPED = "MAPPED";
    public static final String MAPPING_STATUS_UNMAPPED = "UNMAPPED";

    private static final String ROLE_NUTRITION_PLATFORM_ADMIN = "NUTRITION_PLATFORM_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionAccessService accessService;

    @Transactional(readOnly = true)
    public List<StandardFoodResponse> listStandardFoods(RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return standardFoodRepository.findByStatusAndDeletedFalseOrderByIdDesc(NutritionStatus.ACTIVE)
                .stream()
                .map(this::toStandardFoodResponse)
                .toList();
    }

    @Transactional
    public StandardFoodResponse createStandardFood(@Valid @NotNull CreateStandardFoodRequest request,
                                                   RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn(request.nameCn().trim());
        food.setNameEn(trimToNull(request.nameEn()));
        food.setCategory(request.category().trim());
        food.setCaloriesPer100g(request.caloriesPer100g());
        food.setProteinPer100g(request.proteinPer100g());
        food.setFatPer100g(request.fatPer100g());
        food.setCarbsPer100g(request.carbsPer100g());
        food.setDataQuality("MANUAL");
        food.setStatus(NutritionStatus.ACTIVE);
        return toStandardFoodResponse(standardFoodRepository.save(food));
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> listFamilyRecipes(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionRecipePo> recipes = recipeRepository.findByFamilyIdAndStatusAndDeletedFalseOrderByIdDesc(
                familyId, NutritionStatus.ACTIVE);
        if (recipes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<NutritionRecipeIngredientPo>> ingredientsByRecipeId = recipeIngredientRepository
                .findByRecipeIdInAndDeletedFalseOrderByIdAsc(recipes.stream().map(NutritionRecipePo::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(NutritionRecipeIngredientPo::getRecipeId));
        return recipes.stream()
                .map(recipe -> toRecipeResponse(recipe, ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of())))
                .toList();
    }

    @Transactional
    public RecipeResponse createFamilyRecipe(@NotNull Long familyId, @Valid @NotNull CreateRecipeRequest request,
                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(NutritionRecipeSourceType.FAMILY_PRIVATE);
        recipe.setName(request.name().trim());
        recipe.setCategory(trimToNull(request.category()));
        recipe.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : "");
        recipe.setServingCount(request.servingCount() == null ? 1 : request.servingCount());
        recipe.setStatus(NutritionStatus.ACTIVE);
        NutritionRecipePo savedRecipe = recipeRepository.save(recipe);

        List<NutritionRecipeIngredientPo> ingredients = request.ingredients().stream()
                .map(ingredient -> createIngredient(familyId, savedRecipe.getId(), ingredient))
                .map(recipeIngredientRepository::save)
                .sorted(Comparator.comparing(NutritionRecipeIngredientPo::getId))
                .toList();
        return toRecipeResponse(savedRecipe, ingredients);
    }

    private NutritionRecipeIngredientPo createIngredient(Long familyId, Long recipeId,
                                                         RecipeIngredientRequest request) {
        NutritionRecipeIngredientPo ingredient = new NutritionRecipeIngredientPo();
        ingredient.setFamilyId(familyId);
        ingredient.setRecipeId(recipeId);
        ingredient.setRawFoodName(request.foodName().trim());
        ingredient.setAmount(request.amount());
        ingredient.setUnit(request.unit().trim());
        ingredient.setOptional(Boolean.TRUE.equals(request.optional()));
        findStandardFood(request.foodName(), request.category()).ifPresentOrElse(food -> {
            ingredient.setStandardFoodId(food.getId());
            ingredient.setMappingStatus(MAPPING_STATUS_MAPPED);
        }, () -> {
            ingredient.setStandardFoodId(null);
            ingredient.setMappingStatus(MAPPING_STATUS_UNMAPPED);
        });
        return ingredient;
    }

    private Optional<NutritionStandardFoodPo> findStandardFood(String foodName, String category) {
        if (!StringUtils.hasText(foodName) || !StringUtils.hasText(category)) {
            return Optional.empty();
        }
        return standardFoodRepository
                .findFirstByNameCnIgnoreCaseAndCategoryIgnoreCaseAndStatusAndDeletedFalseOrderByIdAsc(
                        foodName.trim(), category.trim(), NutritionStatus.ACTIVE);
    }

    private StandardFoodResponse toStandardFoodResponse(NutritionStandardFoodPo food) {
        return new StandardFoodResponse(food.getId(), food.getNameCn(), food.getNameEn(), food.getCategory(),
                food.getCaloriesPer100g(), food.getProteinPer100g(), food.getFatPer100g(),
                food.getCarbsPer100g(), food.getStatus(), food.getCreatedAt(), food.getUpdatedAt());
    }

    private RecipeResponse toRecipeResponse(NutritionRecipePo recipe, List<NutritionRecipeIngredientPo> ingredients) {
        return new RecipeResponse(recipe.getId(), recipe.getFamilyId(), recipe.getSourceType(), recipe.getName(),
                recipe.getCategory(), recipe.getDescription(), recipe.getServingCount(), recipe.getStatus(),
                ingredients.stream().map(this::toRecipeIngredientResponse).toList(),
                recipe.getCreatedAt(), recipe.getUpdatedAt());
    }

    private RecipeIngredientResponse toRecipeIngredientResponse(NutritionRecipeIngredientPo ingredient) {
        return new RecipeIngredientResponse(ingredient.getId(), ingredient.getRecipeId(),
                ingredient.getStandardFoodId(), ingredient.getRawFoodName(), ingredient.getAmount(),
                ingredient.getUnit(), ingredient.getMappingStatus(), ingredient.isOptional());
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw forbidden();
        }
        return actorId;
    }

    public static void requirePlatformAdmin(RbacPrincipal principal) {
        Set<String> roleCodes = principal == null || principal.roleCodes() == null ? Set.of() : principal.roleCodes();
        if (!roleCodes.contains(ROLE_NUTRITION_PLATFORM_ADMIN) && !roleCodes.contains(ROLE_SUPER_ADMIN)) {
            throw new NutritionException("NUTRITION_PLATFORM_FORBIDDEN", "Nutrition platform access is required");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static NutritionException forbidden() {
        return new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
    }
}
