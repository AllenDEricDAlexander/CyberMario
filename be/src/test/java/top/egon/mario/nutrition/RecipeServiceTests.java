package top.egon.mario.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
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
import top.egon.mario.nutrition.service.RecipeService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
