package top.egon.mario.nutrition.importer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.NutritionImportJobResponse;
import top.egon.mario.nutrition.po.enums.NutritionImportStatus;
import top.egon.mario.nutrition.po.enums.NutritionImportType;
import top.egon.mario.nutrition.po.enums.NutritionRecipeSourceType;
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
import top.egon.mario.nutrition.service.importer.NutritionImportFailureRecorder;
import top.egon.mario.nutrition.service.importer.NutritionImportService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Set;

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

    private static RbacPrincipal platformAdmin() {
        return new RbacPrincipal(9001L, "nutrition-admin",
                Set.of("NUTRITION_PLATFORM_ADMIN"), Set.of(), "v1");
    }

    private static RbacPrincipal nutritionUser(Long userId) {
        return new RbacPrincipal(userId, "nutrition-user-" + userId,
                Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
