package top.egon.mario.nutrition.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import top.egon.mario.common.api.TraceContext;
import top.egon.mario.nutrition.dto.request.CreateDataGrantRequest;
import top.egon.mario.nutrition.dto.request.GenerateAiRecommendationRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationItemRequest;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.request.MealPlanItemRequest;
import top.egon.mario.nutrition.dto.request.NutritionNutrientsRequest;
import top.egon.mario.nutrition.dto.request.NutritionRecordAdjustmentRequest;
import top.egon.mario.nutrition.dto.request.UpdateFamilySettingsRequest;
import top.egon.mario.nutrition.dto.request.UpdateMealPlanRequest;
import top.egon.mario.nutrition.dto.request.UpsertBudgetRuleRequest;
import top.egon.mario.nutrition.dto.response.MemberProfileResponse;
import top.egon.mario.nutrition.dto.response.StandardFoodResponse;
import top.egon.mario.nutrition.po.enums.NutritionGrantDataScope;
import top.egon.mario.nutrition.po.enums.NutritionGrantPermissionLevel;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionMemberType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.service.BudgetService;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.MealConfirmationService;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.nutrition.service.MemberHealthService;
import top.egon.mario.nutrition.service.NutritionRecordService;
import top.egon.mario.nutrition.service.RecipeService;
import top.egon.mario.nutrition.service.ShoppingListService;
import top.egon.mario.nutrition.service.ai.NutritionAiService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionControllerSmokeTests {

    @Test
    void controllersWrapResponsesWithApiResponseAndTraceId() {
        RecipeService recipeService = mock(RecipeService.class);
        RecipeController controller = recipeController(recipeService);
        StandardFoodResponse tomato = standardFood(1L, "Tomato");
        when(recipeService.listStandardFoods(platformAdmin())).thenReturn(List.of(tomato));

        StepVerifier.create(controller.standardFoods(platformAdmin())
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-nutrition-platform")))
                .assertNext(response -> {
                    assertThat(response.code()).isEqualTo("0");
                    assertThat(response.message()).isEqualTo("OK");
                    assertThat(response.traceId()).isEqualTo("trace-nutrition-platform");
                    assertThat(response.data()).containsExactly(tomato);
                })
                .verifyComplete();
    }

    @Test
    void blockingSupportUsesActorIdFromRbacPrincipal() {
        MemberHealthService memberHealthService = mock(MemberHealthService.class);
        MemberHealthController controller = memberHealthController(memberHealthService);
        MemberProfileResponse member = memberProfile(100L, 42L, 77L);
        when(memberHealthService.listMemberProfiles(42L, 77L)).thenReturn(List.of(member));

        StepVerifier.create(controller.members(42L, principal(77L))
                        .contextWrite(context -> context.put(TraceContext.CONTEXT_KEY, "trace-nutrition-family")))
                .assertNext(response -> {
                    assertThat(response.traceId()).isEqualTo("trace-nutrition-family");
                    assertThat(response.data()).containsExactly(member);
                })
                .verifyComplete();

        verify(memberHealthService).listMemberProfiles(42L, 77L);
    }

    @Test
    void familyAdministrationEndpointsForwardSettingsAndGrantRequests() {
        ClanFamilyService service = mock(ClanFamilyService.class);
        ClanFamilyController controller = immediate(new ClanFamilyController(service));
        UpdateFamilySettingsRequest settings = new UpdateFamilySettingsRequest(
                "Shanghai", "CNY", List.of(NutritionMealType.DINNER),
                true, java.time.LocalTime.of(8, 0), true, true);
        CreateDataGrantRequest grant = new CreateDataGrantRequest(
                null, "USER", 88L, NutritionGrantDataScope.HEALTH_PROFILE,
                NutritionGrantPermissionLevel.READ, null);

        StepVerifier.create(controller.updateFamilySettings(42L, settings, principal(77L))).verifyComplete();
        StepVerifier.create(controller.createDataGrant(42L, grant, principal(77L))).verifyComplete();

        verify(service).updateFamilySettings(42L, settings, 77L);
        verify(service).createDataGrant(42L, grant, 77L);
    }

    @Test
    void recipeValidationAndAiGenerationEndpointsCallTheirRealControllerMethods() {
        RecipeService recipeService = mock(RecipeService.class);
        RecipeController recipeController = recipeController(recipeService);
        NutritionAiService aiService = mock(NutritionAiService.class);
        AiRecommendationController aiController = immediate(new AiRecommendationController(aiService));
        GenerateAiRecommendationRequest request = new GenerateAiRecommendationRequest(
                LocalDate.of(2026, 7, 8), List.of(NutritionMealType.DINNER));

        StepVerifier.create(recipeController.validateRecipe(42L, 100L, principal(77L))).verifyComplete();
        StepVerifier.create(aiController.generateRecommendation(42L, request, principal(77L))).verifyComplete();

        verify(recipeService).validateRecipe(42L, 100L, 77L);
        verify(aiService).generateManualRecommendation(
                42L, request.plannedDate(), request.mealTypes(), 77L);
    }

    @Test
    void mealWorkflowEndpointsForwardEditsConfirmationsAndShoppingFinalization() {
        MealPlanService mealPlanService = mock(MealPlanService.class);
        MealPlanController mealPlanController = immediate(new MealPlanController(mealPlanService));
        MealConfirmationService confirmationService = mock(MealConfirmationService.class);
        MealConfirmationController confirmationController = immediate(
                new MealConfirmationController(confirmationService));
        ShoppingListService shoppingListService = mock(ShoppingListService.class);
        ShoppingListController shoppingController = immediate(new ShoppingListController(shoppingListService));
        UpdateMealPlanRequest update = new UpdateMealPlanRequest(3L, Instant.parse("2026-07-08T10:00:00Z"),
                List.of(new MealPlanItemRequest(101L, NutritionMealType.DINNER,
                        201L, new BigDecimal("1.500"), 0)));
        MealConfirmationRequest confirmation = new MealConfirmationRequest(301L, true,
                List.of(new MealConfirmationItemRequest(
                        101L, true, new BigDecimal("1.500"), false, null)), null);

        StepVerifier.create(mealPlanController.updateMealPlan(
                42L, 100L, update, principal(77L))).verifyComplete();
        StepVerifier.create(mealPlanController.publishMealPlan(
                42L, 100L, principal(77L))).verifyComplete();
        StepVerifier.create(confirmationController.confirmMeal(
                42L, 100L, confirmation, principal(77L))).verifyComplete();
        StepVerifier.create(shoppingController.generateShoppingList(
                42L, 100L, principal(77L))).verifyComplete();

        verify(mealPlanService).updateMealPlan(42L, 100L, update, 77L);
        verify(mealPlanService).publishMealPlan(42L, 100L, 77L);
        verify(confirmationService).confirmMeal(42L, 100L, confirmation, 77L);
        verify(shoppingListService).generateFinalShoppingList(42L, 100L, 77L);
    }

    @Test
    void budgetAndRecordEndpointsForwardRulesAndCorrections() {
        BudgetService budgetService = mock(BudgetService.class);
        BudgetController budgetController = immediate(new BudgetController(budgetService));
        NutritionRecordService recordService = mock(NutritionRecordService.class);
        NutritionRecordController recordController = immediate(new NutritionRecordController(recordService));
        UpsertBudgetRuleRequest rule = new UpsertBudgetRuleRequest(
                "Weekly food", "WEEKLY", new BigDecimal("500.00"), "CNY",
                new BigDecimal("0.8000"), true);
        NutritionNutrientsRequest nutrients = new NutritionNutrientsRequest(
                new BigDecimal("50.000"), new BigDecimal("5.000"), new BigDecimal("2.000"),
                new BigDecimal("3.000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        NutritionRecordAdjustmentRequest adjustment = new NutritionRecordAdjustmentRequest(
                nutrients, "ate half portion");

        StepVerifier.create(budgetController.createBudgetRule(
                42L, rule, principal(77L))).verifyComplete();
        StepVerifier.create(recordController.adjustRecord(
                42L, 100L, adjustment, principal(77L))).verifyComplete();
        StepVerifier.create(recordController.familyMonthlyReport(
                42L, LocalDate.of(2026, 7, 1), principal(77L))).verifyComplete();

        verify(budgetService).createBudgetRule(42L, rule, 77L);
        verify(recordService).adjustRecord(42L, 100L, adjustment, 77L);
        verify(recordService).familyMonthlyReport(42L, LocalDate.of(2026, 7, 1), 77L);
    }

    @Test
    void platformAdminApisUsePlatformNutritionPath() {
        assertThat(controllerPaths(RecipeController.class))
                .contains("/api/nutrition/platform/standard-foods")
                .contains("/api/nutrition/platform/standard-foods/{foodId}")
                .contains("/api/nutrition/families/{familyId}/standard-foods")
                .contains("/api/nutrition/platform/recipes")
                .contains("/api/nutrition/platform/recipes/{recipeId}")
                .contains("/api/nutrition/families/{familyId}/recipes")
                .contains("/api/nutrition/families/{familyId}/recipes/{recipeId}")
                .contains("/api/nutrition/families/{familyId}/recipes/{recipeId}/ingredients/{ingredientId}/mapping")
                .contains("/api/nutrition/families/{familyId}/recipes/{recipeId}/validation");

        assertThat(controllerPaths(HealthTagController.class))
                .contains("/api/nutrition/platform/health-tags")
                .contains("/api/nutrition/platform/health-tags/{tagId}")
                .contains("/api/nutrition/families/{familyId}/health-tags");

        assertThat(controllerPaths(NutritionImportController.class))
                .containsExactlyInAnyOrder(
                        "/api/nutrition/platform/import-jobs",
                        "/api/nutrition/platform/import-jobs/{jobId}",
                        "/api/nutrition/platform/import-jobs/{jobId}/confirm"
                );

        assertThat(controllerPaths(MemberHealthController.class))
                .contains("/api/nutrition/families/{familyId}/members")
                .allMatch(path -> path.contains("/families/{familyId}/"));
    }

    private static RecipeController recipeController(RecipeService service) {
        RecipeController controller = new RecipeController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static MemberHealthController memberHealthController(MemberHealthService service) {
        MemberHealthController controller = new MemberHealthController(service);
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static <T extends ReactiveNutritionSupport> T immediate(T controller) {
        ReflectionTestUtils.invokeMethod(controller, "setBlockingScheduler", Schedulers.immediate());
        return controller;
    }

    private static List<String> controllerPaths(Class<?> controllerType) {
        String basePath = firstPath(controllerType.getAnnotation(RequestMapping.class).value(),
                controllerType.getAnnotation(RequestMapping.class).path());
        return Arrays.stream(controllerType.getDeclaredMethods())
                .flatMap(method -> methodPaths(method).stream())
                .map(path -> normalize(basePath, path))
                .toList();
    }

    private static List<String> methodPaths(Method method) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) {
            return paths(getMapping.value(), getMapping.path());
        }
        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) {
            return paths(postMapping.value(), postMapping.path());
        }
        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) {
            return paths(putMapping.value(), putMapping.path());
        }
        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) {
            return paths(deleteMapping.value(), deleteMapping.path());
        }
        return List.of();
    }

    private static List<String> paths(String[] value, String[] path) {
        String[] paths = value.length == 0 ? path : value;
        if (paths.length == 0) {
            return List.of("");
        }
        return Arrays.asList(paths);
    }

    private static String firstPath(String[] value, String[] path) {
        return paths(value, path).getFirst();
    }

    private static String normalize(String basePath, String path) {
        String normalized = (basePath + "/" + path).replaceAll("/{2,}", "/");
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private static StandardFoodResponse standardFood(Long id, String name) {
        return new StandardFoodResponse(id, name, null, List.of("tomato"), "vegetable",
                null, null, BigDecimal.valueOf(18), BigDecimal.valueOf(1.2), BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(3.4), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
                "LOW", BigDecimal.valueOf(15), List.of(), List.of("HEALTHY"), "MANUAL",
                NutritionStatus.ACTIVE, Instant.parse("2026-06-30T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"));
    }

    private static MemberProfileResponse memberProfile(Long id, Long familyId, Long userId) {
        return new MemberProfileResponse(id, familyId, userId, "Luigi", "MALE",
                LocalDate.parse("1990-01-01"), BigDecimal.valueOf(180), BigDecimal.valueOf(72),
                NutritionMemberType.ADULT, true, null, NutritionStatus.ACTIVE,
                Instant.parse("2026-06-30T00:00:00Z"), Instant.parse("2026-06-30T00:00:00Z"));
    }

    private static RbacPrincipal platformAdmin() {
        return new RbacPrincipal(9001L, "nutrition-admin",
                Set.of("NUTRITION_PLATFORM_ADMIN"), Set.of(), "v1");
    }

    private static RbacPrincipal principal(Long userId) {
        return new RbacPrincipal(userId, "user-" + userId, Set.of("NUTRITION_USER"), Set.of(), "v1");
    }
}
