package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.CreateStandardFoodRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.RecipeResponse;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

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
    private NutritionImportJobRepository importJobRepository;
    @Autowired
    private NutritionImportErrorRepository importErrorRepository;

    @BeforeEach
    void setUp() {
        importErrorRepository.deleteAll();
        importJobRepository.deleteAll();
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

        assertThat(publicRecipe.familyId()).isNull();
        assertThat(publicRecipe.sourceType()).isEqualTo(NutritionRecipeSourceType.PLATFORM_PUBLIC);
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
