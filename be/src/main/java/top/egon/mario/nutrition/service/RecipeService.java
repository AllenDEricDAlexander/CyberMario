package top.egon.mario.nutrition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final NutritionStandardFoodRepository standardFoodRepository;
    private final NutritionRecipeRepository recipeRepository;
    private final NutritionRecipeIngredientRepository recipeIngredientRepository;
    private final NutritionAccessService accessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<StandardFoodResponse> listStandardFoods(RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return standardFoodRepository.findByStatusAndDeletedFalseOrderByIdDesc(NutritionStatus.ACTIVE)
                .stream()
                .map(this::toStandardFoodResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StandardFoodResponse> listFamilyStandardFoods(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
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
        applyStandardFood(food, request);
        return toStandardFoodResponse(standardFoodRepository.save(food));
    }

    @Transactional
    public StandardFoodResponse updateStandardFood(@NotNull Long foodId,
                                                   @Valid @NotNull CreateStandardFoodRequest request,
                                                   RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionStandardFoodPo food = getStandardFood(foodId);
        applyStandardFood(food, request);
        return toStandardFoodResponse(standardFoodRepository.save(food));
    }

    @Transactional
    public StandardFoodResponse deactivateStandardFood(@NotNull Long foodId, RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionStandardFoodPo food = getStandardFood(foodId);
        food.setStatus(NutritionStatus.DISABLED);
        return toStandardFoodResponse(standardFoodRepository.save(food));
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> listFamilyRecipes(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionRecipePo> recipes = java.util.stream.Stream.concat(
                        recipeRepository.findByFamilyIdIsNullAndSourceTypeAndStatusAndDeletedFalseOrderByIdDesc(
                                NutritionRecipeSourceType.PLATFORM_PUBLIC, NutritionStatus.ACTIVE).stream(),
                        recipeRepository.findByFamilyIdAndStatusAndDeletedFalseOrderByIdDesc(
                                familyId, NutritionStatus.ACTIVE).stream())
                .sorted(Comparator.comparing(NutritionRecipePo::getId).reversed())
                .toList();
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
        return createRecipe(familyId, NutritionRecipeSourceType.FAMILY_PRIVATE, request);
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> listPlatformRecipes(RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        List<NutritionRecipePo> recipes = recipeRepository
                .findByFamilyIdIsNullAndSourceTypeAndStatusAndDeletedFalseOrderByIdDesc(
                        NutritionRecipeSourceType.PLATFORM_PUBLIC, NutritionStatus.ACTIVE);
        return toRecipeResponses(recipes);
    }

    @Transactional
    public RecipeResponse createPlatformRecipe(@Valid @NotNull CreateRecipeRequest request,
                                               RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        return createRecipe(null, NutritionRecipeSourceType.PLATFORM_PUBLIC, request);
    }

    @Transactional
    public RecipeResponse updatePlatformRecipe(@NotNull Long recipeId,
                                               @Valid @NotNull CreateRecipeRequest request,
                                               RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionRecipePo recipe = getPlatformRecipe(recipeId);
        applyRecipe(recipe, request);
        NutritionRecipePo savedRecipe = recipeRepository.save(recipe);
        List<NutritionRecipeIngredientPo> ingredients = replaceIngredients(null, recipeId, request.ingredients());
        return toRecipeResponse(savedRecipe, ingredients);
    }

    @Transactional
    public RecipeResponse deactivateRecipe(@NotNull Long recipeId, RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionRecipePo recipe = getPlatformRecipe(recipeId);
        recipe.setStatus(NutritionStatus.DISABLED);
        NutritionRecipePo savedRecipe = recipeRepository.save(recipe);
        return toRecipeResponse(savedRecipe,
                recipeIngredientRepository.findByRecipeIdAndDeletedFalseOrderByIdAsc(recipeId));
    }

    private RecipeResponse createRecipe(Long familyId, NutritionRecipeSourceType sourceType,
                                        CreateRecipeRequest request) {
        NutritionRecipePo recipe = new NutritionRecipePo();
        recipe.setFamilyId(familyId);
        recipe.setSourceType(sourceType);
        applyRecipe(recipe, request);
        NutritionRecipePo savedRecipe = recipeRepository.save(recipe);

        List<NutritionRecipeIngredientPo> ingredients = request.ingredients().stream()
                .map(ingredient -> createIngredient(familyId, savedRecipe.getId(), ingredient))
                .map(recipeIngredientRepository::save)
                .sorted(Comparator.comparing(NutritionRecipeIngredientPo::getId))
                .toList();
        return toRecipeResponse(savedRecipe, ingredients);
    }

    private void applyRecipe(NutritionRecipePo recipe, CreateRecipeRequest request) {
        recipe.setName(request.name().trim());
        recipe.setCategory(trimToNull(request.category()));
        recipe.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : "");
        recipe.setServingCount(request.servingCount() == null ? 1 : request.servingCount());
        recipe.setStatus(NutritionStatus.ACTIVE);
        recipe.setDeleted(false);
    }

    private List<NutritionRecipeIngredientPo> replaceIngredients(
            Long familyId, Long recipeId, List<RecipeIngredientRequest> requests) {
        List<NutritionRecipeIngredientPo> existing = recipeIngredientRepository
                .findByRecipeIdAndDeletedFalseOrderByIdAsc(recipeId);
        existing.forEach(ingredient -> ingredient.setDeleted(true));
        recipeIngredientRepository.saveAll(existing);
        return requests.stream()
                .map(request -> createIngredient(familyId, recipeId, request))
                .map(recipeIngredientRepository::save)
                .sorted(Comparator.comparing(NutritionRecipeIngredientPo::getId))
                .toList();
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
        return new StandardFoodResponse(food.getId(), food.getNameCn(), food.getNameEn(),
                readStringList(food.getAliases()), food.getCategory(), food.getExternalSource(),
                food.getExternalFoodId(), food.getCaloriesPer100g(), food.getProteinPer100g(),
                food.getFatPer100g(), food.getCarbsPer100g(), food.getSugarPer100g(),
                food.getSodiumPer100g(), food.getFiberPer100g(), food.getCholesterolPer100g(),
                food.getPurineLevel(), food.getGiValue(), readStringList(food.getAllergenTags()),
                readStringList(food.getSuitableTags()), food.getDataQuality(), food.getStatus(),
                food.getCreatedAt(), food.getUpdatedAt());
    }

    private void applyStandardFood(NutritionStandardFoodPo food, CreateStandardFoodRequest request) {
        food.setNameCn(request.nameCn().trim());
        food.setNameEn(trimToNull(request.nameEn()));
        food.setAliases(writeStringList(request.aliases()));
        food.setCategory(request.category().trim());
        food.setExternalSource(trimToNull(request.externalSource()));
        food.setExternalFoodId(trimToNull(request.externalFoodId()));
        food.setCaloriesPer100g(request.caloriesPer100g());
        food.setProteinPer100g(request.proteinPer100g());
        food.setFatPer100g(request.fatPer100g());
        food.setCarbsPer100g(request.carbsPer100g());
        food.setSugarPer100g(request.sugarPer100g());
        food.setSodiumPer100g(request.sodiumPer100g());
        food.setFiberPer100g(request.fiberPer100g());
        food.setCholesterolPer100g(request.cholesterolPer100g());
        food.setPurineLevel(trimToNull(request.purineLevel()));
        food.setGiValue(request.giValue());
        food.setAllergenTags(writeStringList(request.allergenTags()));
        food.setSuitableTags(writeStringList(request.suitableTags()));
        food.setDataQuality(request.dataQuality().trim());
        food.setStatus(request.status());
        food.setDeleted(false);
    }

    private NutritionStandardFoodPo getStandardFood(Long foodId) {
        return standardFoodRepository.findByIdAndDeletedFalse(foodId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_STANDARD_FOOD_NOT_FOUND", "nutrition standard food not found"));
    }

    private NutritionRecipePo getPlatformRecipe(Long recipeId) {
        return recipeRepository.findByIdAndFamilyIdIsNullAndSourceTypeAndDeletedFalse(
                        recipeId, NutritionRecipeSourceType.PLATFORM_PUBLIC)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found"));
    }

    private List<RecipeResponse> toRecipeResponses(List<NutritionRecipePo> recipes) {
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

    private String writeStringList(List<String> values) {
        List<String> normalized = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition JSON value is invalid");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            return List.of();
        }
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
