package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.CreateStandardFoodRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.request.RecipeStepRequest;
import top.egon.mario.nutrition.dto.request.UpdateRecipeIngredientMappingRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeStepRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.po.NutritionFoodPriceRecordPo;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies family recipes stay scoped and preserve unmapped ingredient names.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class RecipeServiceTests {

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private RecipeService recipeService;
    @Autowired
    private NutritionClanRepository clanRepository;
    @Autowired
    private NutritionFamilyRepository familyRepository;
    @Autowired
    private NutritionClanFamilyRepository clanFamilyRepository;
    @Autowired
    private NutritionMemberProfileRepository memberProfileRepository;
    @Autowired
    private NutritionHealthProfileRepository healthProfileRepository;
    @Autowired
    private NutritionScopedRoleBindingRepository roleBindingRepository;
    @Autowired
    private NutritionDataGrantRepository dataGrantRepository;
    @Autowired
    private NutritionStandardFoodRepository standardFoodRepository;
    @Autowired
    private NutritionRecipeRepository recipeRepository;
    @Autowired
    private NutritionRecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private NutritionRecipeStepRepository recipeStepRepository;
    @Autowired
    private NutritionFoodPriceRecordRepository foodPriceRecordRepository;
    @Autowired
    private NutritionImportJobRepository importJobRepository;
    @Autowired
    private NutritionImportErrorRepository importErrorRepository;

    @BeforeEach
    void setUp() {
        importErrorRepository.deleteAll();
        importJobRepository.deleteAll();
        foodPriceRecordRepository.deleteAll();
        recipeStepRepository.deleteAll();
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        standardFoodRepository.deleteAll();
        dataGrantRepository.deleteAll();
        roleBindingRepository.deleteAll();
        clanFamilyRepository.deleteAll();
        healthProfileRepository.deleteAll();
        memberProfileRepository.deleteAll();
        familyRepository.deleteAll();
        clanRepository.deleteAll();
    }

    @Test
    void recipeIngredientKeepsRawNameWhenStandardFoodIsMissing() {
        Long ownerUserId = 6001L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);

        RecipeResponse recipe = recipeService.createFamilyRecipe(family.id(), new CreateRecipeRequest(
                "Mystery Pasta",
                "DINNER",
                "Family private recipe",
                2,
                List.of(new RecipeIngredientRequest(
                        "Mystery Sauce", "CONDIMENT", new BigDecimal("35.000"), "g", false))
        ), ownerUserId);

        assertThat(recipe.familyId()).isEqualTo(family.id());
        assertThat(recipe.ingredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.rawFoodName()).isEqualTo("Mystery Sauce");
            assertThat(ingredient.standardFoodId()).isNull();
            assertThat(ingredient.mappingStatus()).isEqualTo("UNMAPPED");
        });
        assertThat(recipeIngredientRepository.findAll()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.getFamilyId()).isEqualTo(family.id());
            assertThat(ingredient.getRawFoodName()).isEqualTo("Mystery Sauce");
            assertThat(ingredient.getStandardFoodId()).isNull();
            assertThat(ingredient.getMappingStatus()).isEqualTo("UNMAPPED");
        });
    }

    @Test
    void standardFoodResponseContainsFullPersistedNutrientShape() {
        var response = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());

        assertThat(response.aliases()).containsExactly("whole milk");
        assertThat(response.externalSource()).isEqualTo("USDA");
        assertThat(response.externalFoodId()).isEqualTo("milk-1");
        assertThat(response.sugarPer100g()).isEqualByComparingTo("3.500");
        assertThat(response.sodiumPer100g()).isEqualByComparingTo("12.000");
        assertThat(response.fiberPer100g()).isEqualByComparingTo("0.100");
        assertThat(response.cholesterolPer100g()).isEqualByComparingTo("8.000");
        assertThat(response.purineLevel()).isEqualTo("LOW");
        assertThat(response.giValue()).isEqualByComparingTo("27.000");
        assertThat(response.allergenTags()).containsExactly("MILK");
        assertThat(response.suitableTags()).containsExactly("HIGH_PROTEIN");
        assertThat(response.dataQuality()).isEqualTo("VERIFIED");
        assertThat(response.status()).isEqualTo(NutritionStatus.ACTIVE);
    }

    @Test
    void platformCatalogMutationRequiresPlatformAdministratorButFamilyReaderCanSearch() {
        Long ownerUserId = 6101L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);

        assertThatThrownBy(() -> recipeService.createStandardFood(fullFoodRequest(), nutritionUser(ownerUserId)))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_PLATFORM_FORBIDDEN");

        var created = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
        var visible = recipeService.listFamilyStandardFoods(family.id(), ownerUserId);

        assertThat(visible).extracting(food -> food.id()).containsExactly(created.id());
    }

    @Test
    void familyRecipeListingCombinesPublicAndOwnRecipesOnly() {
        Long marioUserId = 6201L;
        Long peachUserId = 6202L;
        FamilyResponse marioFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), marioUserId);
        FamilyResponse peachFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Peach Family", null, null, List.of(), "Peach"), peachUserId);
        RecipeResponse publicRecipe = recipeService.createPlatformRecipe(
                recipeRequest("Public Soup"), platformAdmin());
        recipeService.createFamilyRecipe(marioFamily.id(), recipeRequest("Mario Pasta"), marioUserId);
        recipeService.createFamilyRecipe(peachFamily.id(), recipeRequest("Peach Cake"), peachUserId);

        List<RecipeResponse> visible = recipeService.listFamilyRecipes(marioFamily.id(), marioUserId);
        RecipeResponse publicDetail = recipeService.getRecipe(marioFamily.id(), publicRecipe.id(), marioUserId);

        assertThat(publicRecipe.familyId()).isNull();
        assertThat(publicRecipe.sourceType()).isEqualTo(NutritionRecipeSourceType.PLATFORM_PUBLIC);
        assertThat(publicDetail.id()).isEqualTo(publicRecipe.id());
        assertThat(visible).extracting(RecipeResponse::name)
                .containsExactlyInAnyOrder("Public Soup", "Mario Pasta")
                .doesNotContain("Peach Cake");
    }

    @Test
    void platformRecipeAndStandardFoodCanBeUpdatedAndDeactivated() {
        var food = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
        var updatedFoodRequest = new CreateStandardFoodRequest(
                "全脂牛奶", "Whole Milk", List.of("milk"), "dairy", "USDA", "milk-2",
                new BigDecimal("65.000"), new BigDecimal("3.200"), new BigDecimal("3.600"),
                new BigDecimal("4.900"), new BigDecimal("3.600"), new BigDecimal("13.000"),
                new BigDecimal("0.200"), new BigDecimal("9.000"), "LOW", new BigDecimal("28.000"),
                List.of("MILK"), List.of("HIGH_PROTEIN"), "VERIFIED", NutritionStatus.ACTIVE);
        var updatedFood = recipeService.updateStandardFood(food.id(), updatedFoodRequest, platformAdmin());
        var recipe = recipeService.createPlatformRecipe(recipeRequest("Public Soup"), platformAdmin());
        var updatedRecipe = recipeService.updatePlatformRecipe(
                recipe.id(), recipeRequest("Public Tomato Soup"), platformAdmin());

        assertThat(updatedFood.nameCn()).isEqualTo("全脂牛奶");
        assertThat(updatedRecipe.name()).isEqualTo("Public Tomato Soup");

        assertThat(recipeService.deactivateStandardFood(food.id(), platformAdmin()).status())
                .isEqualTo(NutritionStatus.DISABLED);
        assertThat(recipeService.deactivateRecipe(recipe.id(), platformAdmin()).status())
                .isEqualTo(NutritionStatus.DISABLED);
    }

    @Test
    void recipeDetailsPersistOrderedStepsFullNutritionAndLatestFamilyCost() {
        Long ownerUserId = 6301L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var food = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
        savePrice(family.id(), food.id(), LocalDate.of(2026, 7, 1), "0.0400");
        savePrice(family.id(), food.id(), LocalDate.of(2026, 7, 12), "0.0500");

        RecipeResponse recipe = recipeService.createFamilyRecipe(family.id(), new CreateRecipeRequest(
                "Milk Bowl", "BREAKFAST", "Warm milk", 2, 15, "EASY",
                List.of("HIGH_PROTEIN"), List.of("MILK"),
                List.of(new RecipeIngredientRequest(food.id(), "Milk", "DAIRY",
                        new BigDecimal("2"), "cup", new BigDecimal("100"), false)),
                List.of(new RecipeStepRequest(2, "Serve", "Serve warm"),
                        new RecipeStepRequest(1, "Heat", "Heat the milk"))
        ), ownerUserId);

        assertThat(recipe.steps()).extracting(step -> step.stepNo()).containsExactly(1, 2);
        assertThat(recipe.ingredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.gramsPerUnit()).isEqualByComparingTo("100");
            assertThat(ingredient.nutritionSnapshot().calories()).isEqualByComparingTo("128.000");
            assertThat(ingredient.nutritionSnapshot().sodium()).isEqualByComparingTo("24.000");
        });
        assertThat(recipe.nutritionSnapshot().calories()).isEqualByComparingTo("128.000");
        assertThat(recipe.nutritionSnapshot().protein()).isEqualByComparingTo("6.200");
        assertThat(recipe.nutritionSnapshot().fat()).isEqualByComparingTo("7.000");
        assertThat(recipe.nutritionSnapshot().carbs()).isEqualByComparingTo("9.600");
        assertThat(recipe.nutritionSnapshot().sugar()).isEqualByComparingTo("7.000");
        assertThat(recipe.nutritionSnapshot().sodium()).isEqualByComparingTo("24.000");
        assertThat(recipe.nutritionSnapshot().fiber()).isEqualByComparingTo("0.200");
        assertThat(recipe.nutritionSnapshot().cholesterol()).isEqualByComparingTo("16.000");
        assertThat(recipe.estimatedCost()).isEqualByComparingTo("10.00");
        assertThat(recipeRepository.findById(recipe.id())).hasValueSatisfying(stored -> {
            assertThat(stored.getNutritionSnapshot()).contains("calories");
            assertThat(stored.getEstimatedCost()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void requiredAndOptionalUnmappedIngredientsProduceErrorsAndWarnings() {
        Long ownerUserId = 6401L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        RecipeResponse recipe = recipeService.createFamilyRecipe(family.id(), new CreateRecipeRequest(
                "Mystery Soup", "DINNER", "", 1, null, null, List.of(), List.of(),
                List.of(
                        new RecipeIngredientRequest(null, "Mystery Base", "SOUP",
                                new BigDecimal("100"), "g", null, false),
                        new RecipeIngredientRequest(null, "Optional Spice", "CONDIMENT",
                                BigDecimal.ONE, "spoon", null, true)),
                List.of()), ownerUserId);

        var validation = recipeService.validateRecipe(family.id(), recipe.id(), ownerUserId);

        assertThat(validation.publishable()).isFalse();
        assertThat(validation.errors()).contains("NUTRITION_RECIPE_INGREDIENT_UNMAPPED");
        assertThat(validation.warnings()).contains("NUTRITION_RECIPE_OPTIONAL_INGREDIENT_UNMAPPED");
    }

    @Test
    void ingredientMappingRecalculatesRecipeAndMakesItPublishable() {
        Long ownerUserId = 6501L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var food = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
        RecipeResponse recipe = recipeService.createFamilyRecipe(family.id(), recipeRequest("Milk Soup"), ownerUserId);
        Long ingredientId = recipe.ingredients().getFirst().id();

        var mapped = recipeService.updateIngredientMapping(family.id(), recipe.id(), ingredientId,
                new UpdateRecipeIngredientMappingRequest(food.id(), null), ownerUserId);
        var validation = recipeService.validateRecipe(family.id(), recipe.id(), ownerUserId);
        RecipeResponse refreshed = recipeService.getRecipe(family.id(), recipe.id(), ownerUserId);

        assertThat(mapped.standardFoodId()).isEqualTo(food.id());
        assertThat(mapped.mappingStatus()).isEqualTo("MAPPED");
        assertThat(validation.publishable()).isTrue();
        assertThat(refreshed.nutritionSnapshot().calories()).isEqualByComparingTo("64.000");
    }

    @Test
    void requiredHouseholdUnitWithoutWeightBlocksRecipeValidation() {
        Long ownerUserId = 6551L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        var food = recipeService.createStandardFood(fullFoodRequest(), platformAdmin());
        RecipeResponse recipe = recipeService.createFamilyRecipe(family.id(), new CreateRecipeRequest(
                "Milk Cup", "BREAKFAST", "", 1, null, null, List.of(), List.of(),
                List.of(new RecipeIngredientRequest(food.id(), "Milk", "DAIRY",
                        BigDecimal.ONE, "cup", null, false)), List.of()), ownerUserId);

        var validation = recipeService.validateRecipe(family.id(), recipe.id(), ownerUserId);

        assertThat(validation.publishable()).isFalse();
        assertThat(validation.errors()).contains("NUTRITION_UNIT_CONVERSION_MISSING");
    }

    @Test
    void recipeDetailRejectsAnotherFamilyAndUpdateReplacesChildren() {
        Long marioUserId = 6601L;
        Long peachUserId = 6602L;
        FamilyResponse marioFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), marioUserId);
        FamilyResponse peachFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Peach Family", null, null, List.of(), "Peach"), peachUserId);
        RecipeResponse recipe = recipeService.createFamilyRecipe(
                marioFamily.id(), recipeRequest("Mario Soup"), marioUserId);

        assertThatThrownBy(() -> recipeService.getRecipe(peachFamily.id(), recipe.id(), peachUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");

        RecipeResponse updated = recipeService.updateFamilyRecipe(marioFamily.id(), recipe.id(),
                new CreateRecipeRequest("Mario Stew", "DINNER", "Updated", 1, 20, "MEDIUM",
                        List.of(), List.of(),
                        List.of(new RecipeIngredientRequest(null, "Potato", "VEGETABLE",
                                new BigDecimal("200"), "g", null, false)),
                        List.of(new RecipeStepRequest(1, null, "Cook slowly"))), marioUserId);

        assertThat(updated.name()).isEqualTo("Mario Stew");
        assertThat(updated.ingredients()).extracting(ingredient -> ingredient.rawFoodName())
                .containsExactly("Potato");
        assertThat(updated.steps()).extracting(step -> step.instruction()).containsExactly("Cook slowly");
        assertThat(recipeIngredientRepository.findByRecipeIdAndDeletedFalseOrderByIdAsc(recipe.id()))
                .hasSize(1);
        assertThat(recipeStepRepository.findByRecipeIdAndDeletedFalseOrderByStepNoAscIdAsc(recipe.id()))
                .hasSize(1);

        assertThat(recipeService.deactivateFamilyRecipe(marioFamily.id(), recipe.id(), marioUserId).status())
                .isEqualTo(NutritionStatus.DISABLED);
        assertThatThrownBy(() -> recipeService.getRecipe(marioFamily.id(), recipe.id(), marioUserId))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_RECIPE_NOT_FOUND");
    }

    private void savePrice(Long familyId, Long foodId, LocalDate priceDate, String normalizedUnitPrice) {
        NutritionFoodPriceRecordPo price = new NutritionFoodPriceRecordPo();
        price.setFamilyId(familyId);
        price.setStandardFoodId(foodId);
        price.setRawFoodName("Milk");
        price.setPriceDate(priceDate);
        price.setSpecUnit("g");
        price.setTotalPrice(new BigDecimal("20.00"));
        price.setNormalizedUnitPrice(new BigDecimal(normalizedUnitPrice));
        price.setSourceType("TEST");
        foodPriceRecordRepository.save(price);
    }

    private CreateStandardFoodRequest fullFoodRequest() {
        return new CreateStandardFoodRequest(
                "牛奶", "Milk", List.of("whole milk"), "dairy", "USDA", "milk-1",
                new BigDecimal("64.000"), new BigDecimal("3.100"), new BigDecimal("3.500"),
                new BigDecimal("4.800"), new BigDecimal("3.500"), new BigDecimal("12.000"),
                new BigDecimal("0.100"), new BigDecimal("8.000"), "LOW", new BigDecimal("27.000"),
                List.of("MILK"), List.of("HIGH_PROTEIN"), "VERIFIED", NutritionStatus.ACTIVE);
    }

    private CreateRecipeRequest recipeRequest(String name) {
        return new CreateRecipeRequest(name, "DINNER", "Catalog recipe", 2,
                List.of(new RecipeIngredientRequest(
                        "Tomato", "VEGETABLE", new BigDecimal("100.000"), "g", false)));
    }

    private RbacPrincipal platformAdmin() {
        return new RbacPrincipal(9001L, "nutrition-admin",
                Set.of("NUTRITION_PLATFORM_ADMIN"), Set.of(), "v1");
    }

    private RbacPrincipal nutritionUser(Long userId) {
        return new RbacPrincipal(userId, "nutrition-user-" + userId,
                Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
