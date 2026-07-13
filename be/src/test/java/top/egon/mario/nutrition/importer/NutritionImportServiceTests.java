package top.egon.mario.nutrition.importer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.request.CreateRecipeRequest;
import top.egon.mario.nutrition.dto.request.RecipeIngredientRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.NutritionImportJobResponse;
import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
import top.egon.mario.nutrition.po.NutritionStandardFoodPo;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionFoodPriceRecordRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.service.importer.NutritionCsvImportTemplate;
import top.egon.mario.nutrition.service.importer.NutritionImportFailureRecorder;
import top.egon.mario.nutrition.service.importer.NutritionImportService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies CSV import validation, warnings and family-scoped confirmation.
 */
@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class NutritionImportServiceTests {

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private NutritionImportService importService;
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
    @Autowired
    private NutritionImportFailureRecorder failureRecorder;
    @Autowired
    private List<NutritionCsvImportTemplate<?>> importers;
    @Autowired
    private NutritionHealthTagRepository healthTagRepository;
    @Autowired
    private NutritionFoodPriceRecordRepository foodPriceRecordRepository;
    @Autowired
    private RecipeService recipeService;

    @BeforeEach
    void setUp() {
        importErrorRepository.deleteAll();
        importJobRepository.deleteAll();
        foodPriceRecordRepository.deleteAll();
        recipeIngredientRepository.deleteAll();
        recipeRepository.deleteAll();
        healthTagRepository.deleteAll();
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
    void everyDeclaredImportTypeHasExactlyOneImporter() {
        assertThat(importers)
                .extracting(NutritionCsvImportTemplate::importType)
                .containsExactlyInAnyOrder(NutritionImportType.values())
                .doesNotHaveDuplicates();
    }

    @Test
    void standardFoodImportRejectsMissingNameAndReportsRowNumber() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name_cn,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g
                        ,vegetable,18.0,1.2,0.1,3.4
                        """
        ), platformAdmin());

        assertThat(job.totalRows()).isEqualTo(1);
        assertThat(job.successRows()).isZero();
        assertThat(job.failedRows()).isEqualTo(1);
        assertThat(job.warningRows()).isZero();
        assertThat(job.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNo()).isEqualTo(2);
            assertThat(error.columnName()).isEqualTo("name_cn");
            assertThat(error.errorCode()).isEqualTo("REQUIRED");
            assertThat(error.severity()).isEqualTo("ERROR");
        });
        assertThat(importErrorRepository.findByImportJobIdOrderByRowNoAsc(job.id()))
                .singleElement()
                .satisfies(error -> assertThat(error.getRowNo()).isEqualTo(2));
    }

    @Test
    void standardFoodImportRejectsHeaderOnlyCsvAndBlocksConfirm() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                "name,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g"
        ), platformAdmin());

        assertThat(job.status()).isEqualTo(NutritionImportStatus.FAILED);
        assertThat(job.totalRows()).isZero();
        assertThat(job.successRows()).isZero();
        assertThat(job.failedRows()).isEqualTo(1);
        assertThat(job.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNo()).isEqualTo(1);
            assertThat(error.errorCode()).isEqualTo("EMPTY_CSV");
            assertThat(error.severity()).isEqualTo("ERROR");
        });

        assertThatThrownBy(() -> importService.confirmImportJob(job.id(), platformAdmin()))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_IMPORT_STATUS_INVALID");
    }

    @Test
    void standardFoodImportWarnsDuplicateNameWithinCategory() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g
                        Tomato,vegetable,18.0,1.2,0.1,3.4
                        Tomato,vegetable,19.0,1.1,0.2,3.6
                        """
        ), platformAdmin());

        assertThat(job.totalRows()).isEqualTo(2);
        assertThat(job.successRows()).isEqualTo(1);
        assertThat(job.failedRows()).isZero();
        assertThat(job.warningRows()).isEqualTo(1);
        assertThat(job.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNo()).isEqualTo(3);
            assertThat(error.columnName()).isEqualTo("name");
            assertThat(error.errorCode()).isEqualTo("DUPLICATE_NAME_IN_CATEGORY");
            assertThat(error.severity()).isEqualTo("WARNING");
        });

        NutritionImportJobResponse confirmed = importService.confirmImportJob(job.id(), platformAdmin());

        assertThat(confirmed.successRows()).isEqualTo(1);
        assertThat(standardFoodRepository.findAll())
                .extracting(food -> food.getNameCn() + ":" + food.getCategory())
                .containsExactly("Tomato:vegetable");
    }

    @Test
    void confirmingCompletedStandardFoodImportReturnsExistingJobWithoutDuplicatingRows() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g
                        Tomato,vegetable,18.0,1.2,0.1,3.4
                        """
        ), platformAdmin());

        NutritionImportJobResponse first = importService.confirmImportJob(job.id(), platformAdmin());
        NutritionImportJobResponse second = importService.confirmImportJob(job.id(), platformAdmin());

        assertThat(first.status()).isEqualTo(NutritionImportStatus.COMPLETED);
        assertThat(second.status()).isEqualTo(NutritionImportStatus.COMPLETED);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(standardFoodRepository.findAll())
                .extracting(food -> food.getNameCn() + ":" + food.getCategory())
                .containsExactly("Tomato:vegetable");
    }

    @Test
    void standardFoodImportPersistsTheFullCatalogShape() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name_cn,name_en,aliases,category,external_source,external_food_id,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g,sugar_per_100g,sodium_per_100g,fiber_per_100g,cholesterol_per_100g,purine_level,gi_value,allergen_tags,suitable_tags,data_quality,status
                        Whole Milk,Milk,whole milk;full milk,DAIRY,USDA,food-1,61,3.2,3.3,4.8,5.1,43,0,10,LOW,27,MILK;LACTOSE,GAIN_MUSCLE,VERIFIED,ACTIVE
                        """
        ), platformAdmin());

        importService.confirmImportJob(job.id(), platformAdmin());

        assertThat(standardFoodRepository.findAll()).singleElement().satisfies(food -> {
            assertThat(food.getNameCn()).isEqualTo("Whole Milk");
            assertThat(food.getNameEn()).isEqualTo("Milk");
            assertThat(food.getAliases()).isEqualTo("[\"whole milk\",\"full milk\"]");
            assertThat(food.getExternalSource()).isEqualTo("USDA");
            assertThat(food.getExternalFoodId()).isEqualTo("food-1");
            assertThat(food.getSugarPer100g()).isEqualByComparingTo("5.1");
            assertThat(food.getSodiumPer100g()).isEqualByComparingTo("43");
            assertThat(food.getFiberPer100g()).isEqualByComparingTo("0");
            assertThat(food.getCholesterolPer100g()).isEqualByComparingTo("10");
            assertThat(food.getPurineLevel()).isEqualTo("LOW");
            assertThat(food.getGiValue()).isEqualByComparingTo("27");
            assertThat(food.getAllergenTags()).isEqualTo("[\"MILK\",\"LACTOSE\"]");
            assertThat(food.getSuitableTags()).isEqualTo("[\"GAIN_MUSCLE\"]");
            assertThat(food.getDataQuality()).isEqualTo("VERIFIED");
            assertThat(food.getStatus()).isEqualTo(top.egon.mario.nutrition.po.enums.NutritionStatus.ACTIVE);
        });
    }

    @Test
    void standardFoodImportRecordsFailedStatusWhenConfirmPersistenceFails() {
        String overlongName = "T".repeat(129);
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g
                        %s,vegetable,18.0,1.2,0.1,3.4
                        """.formatted(overlongName)
        ), platformAdmin());

        assertThat(job.status()).isEqualTo(NutritionImportStatus.PREVIEW_READY);

        assertThatThrownBy(() -> importService.confirmImportJob(job.id(), platformAdmin()))
                .isInstanceOf(RuntimeException.class);

        assertThat(importJobRepository.findById(job.id())).hasValueSatisfying(storedJob -> {
            assertThat(storedJob.getStatus()).isEqualTo(NutritionImportStatus.FAILED);
            assertThat(storedJob.getCompletedAt()).isNotNull();
            assertThat(storedJob.getErrorSummary()).contains("nutrition import confirm failed");
        });
    }

    @Test
    void failureRecorderDoesNotOverwriteCompletedImportJob() {
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.STANDARD_FOOD,
                null,
                "standard-food.csv",
                """
                        name,category,calories_per_100g,protein_per_100g,fat_per_100g,carbs_per_100g
                        Tomato,vegetable,18.0,1.2,0.1,3.4
                        """
        ), platformAdmin());
        NutritionImportJobResponse confirmed = importService.confirmImportJob(job.id(), platformAdmin());

        assertThat(confirmed.status()).isEqualTo(NutritionImportStatus.COMPLETED);

        failureRecorder.recordConfirmFailure(job.id());

        assertThat(importJobRepository.findById(job.id())).hasValueSatisfying(storedJob -> {
            assertThat(storedJob.getStatus()).isEqualTo(NutritionImportStatus.COMPLETED);
            assertThat(storedJob.getErrorSummary()).isNull();
        });
    }

    @Test
    void familyRecipeImportStoresRecipesUnderFamilyOnly() {
        Long ownerUserId = 7001L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        FamilyResponse otherFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Peach Family", null, null, List.of(), "Peach"), 7002L);
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.PRIVATE_RECIPE,
                family.id(),
                "family-recipes.csv",
                """
                        recipe_name,ingredient_name,amount,unit,category
                        Tomato Pasta,Unknown Sauce,25,g,CONDIMENT
                        """
        ), nutritionUser(ownerUserId));

        NutritionImportJobResponse confirmed = importService.confirmImportJob(job.id(), nutritionUser(ownerUserId));

        assertThat(confirmed.familyId()).isEqualTo(family.id());
        assertThat(recipeRepository.findAll()).singleElement().satisfies(recipe -> {
            assertThat(recipe.getFamilyId()).isEqualTo(family.id());
            assertThat(recipe.getFamilyId()).isNotEqualTo(otherFamily.id());
            assertThat(recipe.getSourceType()).isEqualTo(NutritionRecipeSourceType.FAMILY_PRIVATE);
        });
        assertThat(recipeIngredientRepository.findAll()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.getFamilyId()).isEqualTo(family.id());
            assertThat(ingredient.getStandardFoodId()).isNull();
            assertThat(ingredient.getRawFoodName()).isEqualTo("Unknown Sauce");
        });
    }

    @Test
    void healthTagAndPublicRecipeImportsCreatePlatformCatalogRows() {
        NutritionImportJobResponse tagJob = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.ALLERGY_TAG, null, "allergy-tags.csv", """
                tag_code,name,description,sort_order
                MILK,Milk,Milk allergy,10
                """), platformAdmin());
        NutritionImportJobResponse recipeJob = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.PUBLIC_RECIPE, null, "public-recipes.csv", """
                recipe_name,ingredient_name,amount,unit,category
                Tomato Soup,Tomato,200,g,VEGETABLE
                """), platformAdmin());

        importService.confirmImportJob(tagJob.id(), platformAdmin());
        importService.confirmImportJob(recipeJob.id(), platformAdmin());

        assertThat(healthTagRepository.findAll()).singleElement().satisfies(tag -> {
            assertThat(tag.getTagType()).isEqualTo("ALLERGY_TAG");
            assertThat(tag.getTagCode()).isEqualTo("MILK");
        });
        assertThat(recipeRepository.findAll()).singleElement().satisfies(recipe -> {
            assertThat(recipe.getFamilyId()).isNull();
            assertThat(recipe.getSourceType()).isEqualTo(NutritionRecipeSourceType.PLATFORM_PUBLIC);
        });
    }

    @Test
    void familyIngredientMappingRejectsRecipeOwnedByAnotherFamily() {
        Long marioUserId = 7101L;
        Long peachUserId = 7102L;
        FamilyResponse marioFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), marioUserId);
        FamilyResponse peachFamily = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Peach Family", null, null, List.of(), "Peach"), peachUserId);
        var peachRecipe = recipeService.createFamilyRecipe(peachFamily.id(), new CreateRecipeRequest(
                "Peach Soup", "DINNER", "", 1, List.of(new RecipeIngredientRequest(
                "Mystery Food", "VEGETABLE", new BigDecimal("100.000"), "g", false))), peachUserId);
        NutritionStandardFoodPo food = new NutritionStandardFoodPo();
        food.setNameCn("Tomato");
        food.setCategory("VEGETABLE");
        food.setDataQuality("TEST");
        food.setStatus(top.egon.mario.nutrition.po.enums.NutritionStatus.ACTIVE);
        Long foodId = standardFoodRepository.save(food).getId();
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.FAMILY_INGREDIENT_MAPPING, marioFamily.id(), "mapping.csv", """
                recipe_ingredient_id,standard_food_id
                %d,%d
                """.formatted(peachRecipe.ingredients().getFirst().id(), foodId)), nutritionUser(marioUserId));

        assertThatThrownBy(() -> importService.confirmImportJob(job.id(), nutritionUser(marioUserId)))
                .isInstanceOf(NutritionException.class)
                .extracting("code")
                .isEqualTo("NUTRITION_IMPORT_SCOPE_INVALID");
        assertThat(recipeIngredientRepository.findById(peachRecipe.ingredients().getFirst().id()))
                .hasValueSatisfying(ingredient -> assertThat(ingredient.getStandardFoodId()).isNull());
    }

    @Test
    void historicalPriceImportAlwaysWritesTheJobFamily() {
        Long ownerUserId = 7201L;
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of(), "Mario"), ownerUserId);
        NutritionImportJobResponse job = importService.createImportJob(new CreateNutritionImportJobRequest(
                NutritionImportType.HISTORICAL_PRICE, family.id(), "prices.csv", """
                raw_food_name,price_date,total_price,spec_amount,spec_unit,purchase_quantity,normalized_unit_price,channel
                Tomato,2026-07-01,12.50,500,g,1,0.0250,market
                """), nutritionUser(ownerUserId));

        importService.confirmImportJob(job.id(), nutritionUser(ownerUserId));

        assertThat(foodPriceRecordRepository.findAll()).singleElement().satisfies(price -> {
            assertThat(price.getFamilyId()).isEqualTo(family.id());
            assertThat(price.getRawFoodName()).isEqualTo("Tomato");
            assertThat(price.getTotalPrice()).isEqualByComparingTo("12.50");
        });
    }

    private static RbacPrincipal platformAdmin() {
        return new RbacPrincipal(9001L, "nutrition-admin",
                Set.of("NUTRITION_PLATFORM_ADMIN"), Set.of(), "v1");
    }

    private static RbacPrincipal nutritionUser(Long userId) {
        return new RbacPrincipal(userId, "nutrition-user-" + userId,
                Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
