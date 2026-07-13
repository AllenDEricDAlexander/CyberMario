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
import top.egon.mario.nutrition.dto.request.RecipeStepRequest;
import top.egon.mario.nutrition.dto.request.UpdateRecipeIngredientMappingRequest;
import top.egon.mario.nutrition.dto.response.RecipeIngredientResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.dto.response.RecipeStepResponse;
import top.egon.mario.nutrition.dto.response.RecipeValidationResponse;
import top.egon.mario.nutrition.dto.response.StandardFoodResponse;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.nutrition.po.NutritionRecipeIngredientPo;
import top.egon.mario.nutrition.po.NutritionRecipePo;
import top.egon.mario.nutrition.po.NutritionRecipeStepPo;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeStepRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.nutrition.service.calculation.NutritionCalculationService;
import top.egon.mario.nutrition.service.calculation.NutritionTotals;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    private final NutritionRecipeStepRepository recipeStepRepository;
    private final NutritionFoodPriceRecordRepository foodPriceRecordRepository;
    private final NutritionCalculationService calculationService;
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
        return toRecipeResponses(recipes);
    }

    @Transactional
    public RecipeResponse createFamilyRecipe(@NotNull Long familyId, @Valid @NotNull CreateRecipeRequest request,
                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        return createRecipe(familyId, NutritionRecipeSourceType.FAMILY_PRIVATE, request);
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(@NotNull Long familyId, @NotNull Long recipeId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionRecipePo recipe = getVisibleRecipe(familyId, recipeId);
        return toRecipeResponse(recipe, ingredients(recipeId), steps(recipeId));
    }

    @Transactional
    public RecipeResponse updateFamilyRecipe(@NotNull Long familyId, @NotNull Long recipeId,
                                             @Valid @NotNull CreateRecipeRequest request, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionRecipePo recipe = getFamilyRecipe(familyId, recipeId);
        applyRecipe(recipe, request);
        List<NutritionRecipeIngredientPo> ingredients = replaceIngredients(
                familyId, recipeId, request.ingredients());
        List<NutritionRecipeStepPo> steps = replaceSteps(familyId, recipeId, request.steps());
        refreshRecipeSnapshots(recipe, ingredients);
        return toRecipeResponse(recipe, ingredients, steps);
    }

    @Transactional
    public RecipeIngredientResponse updateIngredientMapping(
            @NotNull Long familyId, @NotNull Long recipeId, @NotNull Long ingredientId,
            @Valid @NotNull UpdateRecipeIngredientMappingRequest request, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionRecipePo recipe = getFamilyRecipe(familyId, recipeId);
        NutritionRecipeIngredientPo ingredient = recipeIngredientRepository.findByIdAndDeletedFalse(ingredientId)
                .filter(candidate -> recipeId.equals(candidate.getRecipeId()))
                .filter(candidate -> familyId.equals(candidate.getFamilyId()))
                .orElseThrow(this::recipeIngredientNotFound);
        requireActiveStandardFood(request.standardFoodId());
        ingredient.setStandardFoodId(request.standardFoodId());
        ingredient.setMappingStatus(MAPPING_STATUS_MAPPED);
        ingredient.setMetadataJson(writeIngredientMetadata(request.gramsPerUnit()));
        recipeIngredientRepository.save(ingredient);
        refreshRecipeSnapshots(recipe, ingredients(recipeId));
        return toRecipeIngredientResponse(ingredient);
    }

    @Transactional(readOnly = true)
    public RecipeValidationResponse validateRecipe(@NotNull Long familyId, @NotNull Long recipeId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        getVisibleRecipe(familyId, recipeId);
        Set<String> errors = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        List<NutritionRecipeIngredientPo> ingredients = ingredients(recipeId);
        Map<Long, NutritionStandardFoodPo> foods = activeFoods(ingredients);
        for (NutritionRecipeIngredientPo ingredient : ingredients) {
            NutritionStandardFoodPo food = ingredient.getStandardFoodId() == null
                    ? null : foods.get(ingredient.getStandardFoodId());
            if (food == null) {
                addValidationIssue(ingredient, errors, warnings,
                        "NUTRITION_RECIPE_INGREDIENT_UNMAPPED",
                        "NUTRITION_RECIPE_OPTIONAL_INGREDIENT_UNMAPPED");
                continue;
            }
            try {
                calculationService.ingredientGrams(ingredient);
            } catch (NutritionException ex) {
                addValidationIssue(ingredient, errors, warnings, ex.getCode(),
                        "NUTRITION_RECIPE_OPTIONAL_UNIT_CONVERSION_MISSING");
            }
        }
        return new RecipeValidationResponse(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    @Transactional
    public RecipeResponse deactivateFamilyRecipe(@NotNull Long familyId, @NotNull Long recipeId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        NutritionRecipePo recipe = getFamilyRecipe(familyId, recipeId);
        recipe.setStatus(NutritionStatus.DISABLED);
        recipeRepository.save(recipe);
        return toRecipeResponse(recipe, ingredients(recipeId), steps(recipeId));
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
        List<NutritionRecipeIngredientPo> ingredients = replaceIngredients(null, recipeId, request.ingredients());
        List<NutritionRecipeStepPo> steps = replaceSteps(null, recipeId, request.steps());
        refreshRecipeSnapshots(recipe, ingredients);
        return toRecipeResponse(recipe, ingredients, steps);
    }

    @Transactional
    public RecipeResponse deactivateRecipe(@NotNull Long recipeId, RbacPrincipal principal) {
        requirePlatformAdmin(principal);
        NutritionRecipePo recipe = getPlatformRecipe(recipeId);
        recipe.setStatus(NutritionStatus.DISABLED);
        recipeRepository.save(recipe);
        return toRecipeResponse(recipe, ingredients(recipeId), steps(recipeId));
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
        List<NutritionRecipeStepPo> steps = createSteps(familyId, savedRecipe.getId(), request.steps());
        refreshRecipeSnapshots(savedRecipe, ingredients);
        return toRecipeResponse(savedRecipe, ingredients, steps);
    }

    private void applyRecipe(NutritionRecipePo recipe, CreateRecipeRequest request) {
        recipe.setName(request.name().trim());
        recipe.setCategory(trimToNull(request.category()));
        recipe.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : "");
        recipe.setServingCount(request.servingCount() == null ? 1 : request.servingCount());
        recipe.setCookingMinutes(request.cookingMinutes());
        recipe.setDifficultyLevel(trimToNull(request.difficultyLevel()));
        recipe.setSuitableTags(writeStringList(request.suitableTags()));
        recipe.setAllergenTags(writeStringList(request.allergenTags()));
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

    private List<NutritionRecipeStepPo> replaceSteps(
            Long familyId, Long recipeId, List<RecipeStepRequest> requests) {
        List<NutritionRecipeStepPo> existing = recipeStepRepository
                .findByRecipeIdAndDeletedFalseOrderByStepNoAscIdAsc(recipeId);
        existing.forEach(step -> step.setDeleted(true));
        recipeStepRepository.saveAll(existing);
        return createSteps(familyId, recipeId, requests);
    }

    private List<NutritionRecipeStepPo> createSteps(
            Long familyId, Long recipeId, List<RecipeStepRequest> requests) {
        List<RecipeStepRequest> values = requests == null ? List.of() : requests;
        Set<Integer> stepNumbers = values.stream().map(RecipeStepRequest::stepNo).collect(Collectors.toSet());
        if (stepNumbers.size() != values.size()) {
            throw new NutritionException(
                    "NUTRITION_RECIPE_STEP_DUPLICATE", "nutrition recipe step number must be unique");
        }
        return values.stream()
                .map(request -> {
                    NutritionRecipeStepPo step = new NutritionRecipeStepPo();
                    step.setFamilyId(familyId);
                    step.setRecipeId(recipeId);
                    step.setStepNo(request.stepNo());
                    step.setTitle(trimToNull(request.title()));
                    step.setInstruction(request.instruction().trim());
                    return recipeStepRepository.save(step);
                })
                .sorted(Comparator.comparingInt(NutritionRecipeStepPo::getStepNo)
                        .thenComparing(NutritionRecipeStepPo::getId))
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
        ingredient.setMetadataJson(writeIngredientMetadata(request.gramsPerUnit()));
        Optional<NutritionStandardFoodPo> requestedFood = request.standardFoodId() == null
                ? findStandardFood(request.foodName(), request.category())
                : Optional.of(requireActiveStandardFood(request.standardFoodId()));
        requestedFood.ifPresentOrElse(food -> {
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

    private NutritionStandardFoodPo requireActiveStandardFood(Long foodId) {
        return standardFoodRepository.findByIdAndDeletedFalse(foodId)
                .filter(food -> NutritionStatus.ACTIVE == food.getStatus())
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
        Map<Long, List<NutritionRecipeStepPo>> stepsByRecipeId = recipeStepRepository
                .findByRecipeIdInAndDeletedFalseOrderByStepNoAscIdAsc(
                        recipes.stream().map(NutritionRecipePo::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(NutritionRecipeStepPo::getRecipeId));
        return recipes.stream()
                .map(recipe -> toRecipeResponse(recipe,
                        ingredientsByRecipeId.getOrDefault(recipe.getId(), List.of()),
                        stepsByRecipeId.getOrDefault(recipe.getId(), List.of())))
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

    private RecipeResponse toRecipeResponse(NutritionRecipePo recipe,
                                            List<NutritionRecipeIngredientPo> ingredients,
                                            List<NutritionRecipeStepPo> steps) {
        return new RecipeResponse(recipe.getId(), recipe.getFamilyId(), recipe.getSourceType(), recipe.getName(),
                recipe.getCategory(), recipe.getDescription(), recipe.getServingCount(), recipe.getCookingMinutes(),
                recipe.getDifficultyLevel(), readStringList(recipe.getSuitableTags()),
                readStringList(recipe.getAllergenTags()), readNutritionTotals(recipe.getNutritionSnapshot()),
                recipe.getEstimatedCost(), recipe.getStatus(),
                ingredients.stream().map(this::toRecipeIngredientResponse).toList(),
                steps.stream().map(this::toRecipeStepResponse).toList(),
                recipe.getCreatedAt(), recipe.getUpdatedAt());
    }

    private RecipeIngredientResponse toRecipeIngredientResponse(NutritionRecipeIngredientPo ingredient) {
        return new RecipeIngredientResponse(ingredient.getId(), ingredient.getRecipeId(),
                ingredient.getStandardFoodId(), ingredient.getRawFoodName(), ingredient.getAmount(),
                ingredient.getUnit(), readGramsPerUnit(ingredient.getMetadataJson()),
                ingredient.getMappingStatus(), ingredient.isOptional(),
                readNutritionTotals(ingredient.getNutritionSnapshot()));
    }

    private RecipeStepResponse toRecipeStepResponse(NutritionRecipeStepPo step) {
        return new RecipeStepResponse(
                step.getId(), step.getRecipeId(), step.getStepNo(), step.getTitle(), step.getInstruction());
    }

    private NutritionRecipePo getVisibleRecipe(Long familyId, Long recipeId) {
        return recipeRepository.findByIdAndStatusAndDeletedFalse(recipeId, NutritionStatus.ACTIVE)
                .filter(recipe -> isVisibleRecipe(familyId, recipe))
                .orElseThrow(this::recipeNotFound);
    }

    private NutritionRecipePo getFamilyRecipe(Long familyId, Long recipeId) {
        return recipeRepository.findByIdAndStatusAndDeletedFalse(recipeId, NutritionStatus.ACTIVE)
                .filter(recipe -> familyId.equals(recipe.getFamilyId()))
                .filter(recipe -> NutritionRecipeSourceType.PLATFORM_PUBLIC != recipe.getSourceType())
                .orElseThrow(this::recipeNotFound);
    }

    private boolean isVisibleRecipe(Long familyId, NutritionRecipePo recipe) {
        return recipe.getFamilyId() == null
                ? NutritionRecipeSourceType.PLATFORM_PUBLIC == recipe.getSourceType()
                : familyId.equals(recipe.getFamilyId());
    }

    private List<NutritionRecipeIngredientPo> ingredients(Long recipeId) {
        return recipeIngredientRepository.findByRecipeIdAndDeletedFalseOrderByIdAsc(recipeId);
    }

    private List<NutritionRecipeStepPo> steps(Long recipeId) {
        return recipeStepRepository.findByRecipeIdAndDeletedFalseOrderByStepNoAscIdAsc(recipeId);
    }

    private void refreshRecipeSnapshots(NutritionRecipePo recipe, List<NutritionRecipeIngredientPo> ingredients) {
        Map<Long, NutritionStandardFoodPo> foods = activeFoods(ingredients);
        NutritionTotals totals = NutritionTotals.zero();
        for (NutritionRecipeIngredientPo ingredient : ingredients) {
            NutritionTotals contribution = NutritionTotals.zero();
            NutritionStandardFoodPo food = ingredient.getStandardFoodId() == null
                    ? null : foods.get(ingredient.getStandardFoodId());
            if (food != null) {
                try {
                    contribution = calculationService.calculateIngredient(ingredient, food);
                } catch (NutritionException ignored) {
                    // Validation retains the precise missing-conversion result for publish checks.
                }
            }
            ingredient.setNutritionSnapshot(writeJson(contribution));
            totals = totals.plus(contribution);
        }
        recipeIngredientRepository.saveAll(ingredients);
        recipe.setNutritionSnapshot(writeJson(totals));
        recipe.setEstimatedCost(calculateEstimatedCost(recipe.getFamilyId(), ingredients));
        recipeRepository.save(recipe);
    }

    private Map<Long, NutritionStandardFoodPo> activeFoods(List<NutritionRecipeIngredientPo> ingredients) {
        List<Long> foodIds = ingredients.stream()
                .map(NutritionRecipeIngredientPo::getStandardFoodId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return standardFoodRepository.findAllById(foodIds).stream()
                .filter(food -> !food.isDeleted())
                .filter(food -> NutritionStatus.ACTIVE == food.getStatus())
                .collect(Collectors.toMap(NutritionStandardFoodPo::getId, food -> food));
    }

    private BigDecimal calculateEstimatedCost(Long familyId, List<NutritionRecipeIngredientPo> ingredients) {
        if (familyId == null || ingredients.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean priced = false;
        for (NutritionRecipeIngredientPo ingredient : ingredients) {
            if (ingredient.getStandardFoodId() == null) {
                if (ingredient.isOptional()) {
                    continue;
                }
                return null;
            }
            BigDecimal grams;
            try {
                grams = calculationService.ingredientGrams(ingredient);
            } catch (NutritionException ex) {
                if (ingredient.isOptional()) {
                    continue;
                }
                return null;
            }
            Optional<NutritionFoodPriceRecordPo> price = foodPriceRecordRepository
                    .findFirstByFamilyIdAndStandardFoodIdAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                            familyId, ingredient.getStandardFoodId(), "g");
            if (price.isEmpty()) {
                price = foodPriceRecordRepository
                        .findFirstByFamilyIdAndRawFoodNameIgnoreCaseAndSpecUnitIgnoreCaseAndDeletedFalseOrderByPriceDateDescIdDesc(
                                familyId, ingredient.getRawFoodName(), "g");
            }
            BigDecimal unitPrice = price.map(NutritionFoodPriceRecordPo::getNormalizedUnitPrice).orElse(null);
            if (unitPrice == null) {
                if (ingredient.isOptional()) {
                    continue;
                }
                return null;
            }
            total = total.add(unitPrice.multiply(grams));
            priced = true;
        }
        return priced ? total.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private void addValidationIssue(NutritionRecipeIngredientPo ingredient,
                                    Set<String> errors, Set<String> warnings,
                                    String errorCode, String warningCode) {
        if (ingredient.isOptional()) {
            warnings.add(warningCode);
        } else {
            errors.add(errorCode);
        }
    }

    private String writeIngredientMetadata(BigDecimal gramsPerUnit) {
        return gramsPerUnit == null ? "{}" : writeJson(Map.of("gramsPerUnit", gramsPerUnit));
    }

    private BigDecimal readGramsPerUnit(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return null;
        }
        try {
            var value = objectMapper.readTree(metadataJson).get("gramsPerUnit");
            return value == null || value.isNull() ? null : value.decimalValue();
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private NutritionTotals readNutritionTotals(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return NutritionTotals.zero();
        }
        try {
            return objectMapper.readValue(json, NutritionTotals.class);
        } catch (JsonProcessingException ignored) {
            return NutritionTotals.zero();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition JSON value is invalid");
        }
    }

    private NutritionException recipeNotFound() {
        return new NutritionException("NUTRITION_RECIPE_NOT_FOUND", "nutrition recipe not found");
    }

    private NutritionException recipeIngredientNotFound() {
        return new NutritionException(
                "NUTRITION_RECIPE_INGREDIENT_NOT_FOUND", "nutrition recipe ingredient not found");
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
