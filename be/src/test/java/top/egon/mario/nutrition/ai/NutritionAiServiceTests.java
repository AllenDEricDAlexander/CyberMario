package top.egon.mario.nutrition.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.SmartLifecycle;
import top.egon.mario.nutrition.dto.request.CreateFamilyRequest;
import top.egon.mario.nutrition.dto.response.FamilyResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationJobRepository;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationRepository;
import top.egon.mario.nutrition.repository.NutritionClanFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionClanRepository;
import top.egon.mario.nutrition.repository.NutritionDataGrantRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionHealthProfileRepository;
import top.egon.mario.nutrition.repository.NutritionImportErrorRepository;
import top.egon.mario.nutrition.repository.NutritionImportJobRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.repository.NutritionMemberProfileRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeIngredientRepository;
import top.egon.mario.nutrition.repository.NutritionRecipeRepository;
import top.egon.mario.nutrition.repository.NutritionRiskCheckResultRepository;
import top.egon.mario.nutrition.repository.NutritionScopedRoleBindingRepository;
import top.egon.mario.nutrition.repository.NutritionStandardFoodRepository;
import top.egon.mario.nutrition.service.ClanFamilyService;
import top.egon.mario.nutrition.service.ai.NutritionAiModelClient;
import top.egon.mario.nutrition.service.ai.NutritionAiModelRequest;
import top.egon.mario.nutrition.service.ai.NutritionAiRecommendationScheduler;
import top.egon.mario.nutrition.service.ai.NutritionAiService;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies AI recommendations remain review-only drafts and persist job failures.
 */
@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-api-key",
        "mario.nutrition.ai.recommendation.runner.enabled=false"
})
class NutritionAiServiceTests {

    @Autowired
    private ClanFamilyService clanFamilyService;
    @Autowired
    private NutritionAiService aiService;
    @Autowired
    private NutritionAiRecommendationScheduler scheduler;
    @Autowired
    private FakeNutritionAiModelClient modelClient;
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
    private NutritionRiskCheckResultRepository riskCheckResultRepository;
    @Autowired
    private NutritionAiRecommendationJobRepository aiJobRepository;
    @Autowired
    private NutritionAiRecommendationRepository aiRecommendationRepository;
    @Autowired
    private NutritionMealPlanRepository mealPlanRepository;
    @Autowired
    private NutritionMealPlanItemRepository mealPlanItemRepository;

    @BeforeEach
    void setUp() {
        modelClient.reset();
        mealPlanItemRepository.deleteAll();
        mealPlanRepository.deleteAll();
        aiRecommendationRepository.deleteAll();
        aiJobRepository.deleteAll();
        riskCheckResultRepository.deleteAll();
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
    void scheduledGenerationCreatesPendingReviewMealPlan() {
        FamilyResponse family = createAiFamily(8101L, LocalTime.of(8, 0));
        modelClient.addResponse(menuJson("Family dinner", "Tomato Pasta"));

        var jobs = scheduler.generateDueRecommendations(LocalDate.of(2026, 7, 1), LocalTime.of(8, 5));

        assertThat(jobs).hasSize(1);
        assertThat(aiJobRepository.findAll()).singleElement().satisfies(job -> {
            assertThat(job.getFamilyId()).isEqualTo(family.id());
            assertThat(job.getTriggerType()).isEqualTo(NutritionAiTriggerType.SCHEDULED);
            assertThat(job.getStatus()).isEqualTo(NutritionAiJobStatus.SUCCEEDED);
            assertThat(job.getRequestedBy()).isNull();
            assertThat(job.getInputSnapshot()).contains("Mario Family", "DINNER");
            assertThat(job.getOutputSnapshot()).contains("Tomato Pasta");
            assertThat(job.getMetadataJson()).contains("normalizedOutput");
            assertThat(job.getErrorMessage()).isNull();
        });
        assertThat(aiRecommendationRepository.findAll()).singleElement().satisfies(recommendation -> {
            assertThat(recommendation.getFamilyId()).isEqualTo(family.id());
            assertThat(recommendation.getTitle()).isEqualTo("Family dinner");
            assertThat(recommendation.getMealTypes()).contains("DINNER");
        });
        assertThat(mealPlanRepository.findAll()).singleElement().satisfies(plan -> {
            assertThat(plan.getFamilyId()).isEqualTo(family.id());
            assertThat(plan.getStatus()).isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW);
            assertThat(plan.getPublishedAt()).isNull();
        });
        assertThat(mealPlanRepository.findAll())
                .noneSatisfy(plan -> assertThat(plan.getStatus()).isEqualTo(NutritionMealPlanStatus.PUBLISHED));
        assertThat(mealPlanItemRepository.findAll()).singleElement().satisfies(item -> {
            assertThat(item.getMealType()).isEqualTo(NutritionMealType.DINNER);
            assertThat(item.getDishName()).isEqualTo("Tomato Pasta");
        });
    }

    @Test
    void aiFailureStoresFailedJobWithoutPublishingMenu() {
        FamilyResponse family = createAiFamily(8102L, LocalTime.of(8, 0));
        modelClient.addFailure(new IllegalStateException("model unavailable"));

        var job = aiService.generateManualRecommendation(
                family.id(), LocalDate.of(2026, 7, 2), List.of(NutritionMealType.DINNER), 8102L);

        assertThat(job.status()).isEqualTo(NutritionAiJobStatus.FAILED);
        assertThat(aiJobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getFamilyId()).isEqualTo(family.id());
            assertThat(saved.getStatus()).isEqualTo(NutritionAiJobStatus.FAILED);
            assertThat(saved.getErrorMessage()).contains("model unavailable");
        });
        assertThat(aiRecommendationRepository.findAll()).isEmpty();
        assertThat(mealPlanRepository.findAll()).isEmpty();
        assertThat(mealPlanItemRepository.findAll()).isEmpty();
    }

    @Test
    void invalidEmptyRecipeDraftFailsJobWithoutMenu() {
        FamilyResponse family = createAiFamily(8104L, LocalTime.of(8, 0));
        modelClient.addResponse("""
                {"title":"Empty dinner","reason":"missing dishes","mealTypes":["DINNER"],"recipes":[],"costEstimate":12.50}
                """);

        var job = aiService.generateManualRecommendation(
                family.id(), LocalDate.of(2026, 7, 4), List.of(NutritionMealType.DINNER), 8104L);

        assertThat(job.status()).isEqualTo(NutritionAiJobStatus.FAILED);
        assertThat(aiJobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionAiJobStatus.FAILED);
            assertThat(saved.getErrorMessage()).contains("NUTRITION_AI_OUTPUT_INVALID");
        });
        assertThat(aiRecommendationRepository.findAll()).isEmpty();
        assertThat(mealPlanRepository.findAll()).isEmpty();
        assertThat(mealPlanItemRepository.findAll()).isEmpty();
    }

    @Test
    void invalidRecipeMealTypeMismatchFailsJobWithoutMenu() {
        FamilyResponse family = createAiFamily(8105L, LocalTime.of(8, 0));
        modelClient.addResponse("""
                {"title":"Mismatch dinner","reason":"wrong meal","mealTypes":["DINNER"],"recipes":[{"mealType":"BREAKFAST","name":"Oatmeal","servingCount":2,"reason":"wrong slot"}],"costEstimate":12.50}
                """);

        var job = aiService.generateManualRecommendation(
                family.id(), LocalDate.of(2026, 7, 5), List.of(NutritionMealType.DINNER), 8105L);

        assertThat(job.status()).isEqualTo(NutritionAiJobStatus.FAILED);
        assertThat(aiJobRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getStatus()).isEqualTo(NutritionAiJobStatus.FAILED);
            assertThat(saved.getErrorMessage()).contains("NUTRITION_AI_OUTPUT_INVALID");
        });
        assertThat(aiRecommendationRepository.findAll()).isEmpty();
        assertThat(mealPlanRepository.findAll()).isEmpty();
        assertThat(mealPlanItemRepository.findAll()).isEmpty();
    }

    @Test
    void manualRegenerationCreatesNewJobForFamilyAndDate() {
        FamilyResponse family = createAiFamily(8103L, LocalTime.of(8, 0));
        LocalDate plannedDate = LocalDate.of(2026, 7, 3);
        modelClient.addResponse(menuJson("First dinner", "Tomato Pasta"));
        modelClient.addResponse(menuJson("Second dinner", "Vegetable Curry"));

        var first = aiService.generateManualRecommendation(
                family.id(), plannedDate, List.of(NutritionMealType.DINNER), 8103L);
        var second = aiService.generateManualRecommendation(
                family.id(), plannedDate, List.of(NutritionMealType.DINNER), 8103L);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.plannedDate()).isEqualTo(plannedDate);
        assertThat(second.plannedDate()).isEqualTo(plannedDate);
        assertThat(aiJobRepository.findAll())
                .hasSize(2)
                .extracting(job -> job.getStatus())
                .containsOnly(NutritionAiJobStatus.SUCCEEDED);
        assertThat(aiRecommendationRepository.findAll()).hasSize(2);
        assertThat(mealPlanRepository.findAll())
                .hasSize(2)
                .allSatisfy(plan -> assertThat(plan.getStatus()).isEqualTo(NutritionMealPlanStatus.PENDING_REVIEW));
        assertThat(mealPlanItemRepository.findAll())
                .extracting(item -> item.getDishName())
                .containsExactlyInAnyOrder("Tomato Pasta", "Vegetable Curry");
    }

    @Test
    void scheduledGenerationReturnsExistingSucceededJobWithoutCallingModelAgain() {
        FamilyResponse family = createAiFamily(8106L, LocalTime.of(8, 0));
        LocalDate plannedDate = LocalDate.of(2026, 7, 6);
        modelClient.addResponse(menuJson("First scheduled dinner", "Tomato Pasta"));
        var first = aiService.generateScheduledRecommendation(family.id(), plannedDate);

        var second = aiService.generateScheduledRecommendation(family.id(), plannedDate);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.recommendationId()).isEqualTo(first.recommendationId());
        assertThat(second.mealPlanId()).isEqualTo(first.mealPlanId());
        assertThat(modelClient.calls()).isEqualTo(1);
        assertThat(aiJobRepository.findAll()).hasSize(1);
        assertThat(aiRecommendationRepository.findAll()).hasSize(1);
        assertThat(mealPlanRepository.findAll()).hasSize(1);
    }

    @Test
    void scheduledGenerationReturnsExistingPendingJobWithoutCallingModel() {
        FamilyResponse family = createAiFamily(8107L, LocalTime.of(8, 0));
        LocalDate plannedDate = LocalDate.of(2026, 7, 7);
        NutritionAiRecommendationJobPo pending = new NutritionAiRecommendationJobPo();
        pending.setFamilyId(family.id());
        pending.setTriggerType(NutritionAiTriggerType.SCHEDULED);
        pending.setStatus(NutritionAiJobStatus.PENDING);
        pending.setPlannedDate(plannedDate);
        pending.setTargetMealTypes("[\"DINNER\"]");
        pending.setInputSnapshot("{}");
        pending.setOutputSnapshot("{}");
        pending.setMetadataJson("{}");
        aiJobRepository.saveAndFlush(pending);

        var job = aiService.generateScheduledRecommendation(family.id(), plannedDate);

        assertThat(job.id()).isEqualTo(pending.getId());
        assertThat(job.status()).isEqualTo(NutritionAiJobStatus.PENDING);
        assertThat(job.recommendationId()).isNull();
        assertThat(job.mealPlanId()).isNull();
        assertThat(modelClient.calls()).isZero();
        assertThat(aiJobRepository.findAll()).hasSize(1);
        assertThat(aiRecommendationRepository.findAll()).isEmpty();
        assertThat(mealPlanRepository.findAll()).isEmpty();
    }

    @Test
    void runnerTriggersDueRecommendationScanAndContinuesAfterFailure() throws Exception {
        CountDownLatch secondInvocation = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        NutritionAiRecommendationScheduler scheduler = new NutritionAiRecommendationScheduler(null, null) {
            @Override
            public List<NutritionAiRecommendationJobResponse> generateDueRecommendations(LocalDate date, LocalTime now) {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("forced runner failure");
                }
                secondInvocation.countDown();
                return List.of();
            }
        };
        SmartLifecycle runner = newRecommendationRunner(scheduler, 0L, 10L);
        try {
            runner.start();

            assertThat(secondInvocation.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            runner.stop();
        }
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    }

    private FamilyResponse createAiFamily(Long ownerUserId, LocalTime generateTime) {
        FamilyResponse family = clanFamilyService.createFamily(new CreateFamilyRequest(
                "Mario Family", null, null, List.of("DINNER"), "Mario"), ownerUserId);
        NutritionFamilyPo po = familyRepository.findById(family.id()).orElseThrow();
        po.setAiEnabled(true);
        po.setAiGenerateTime(generateTime);
        familyRepository.saveAndFlush(po);
        return family;
    }

    private String menuJson(String title, String dishName) {
        return """
                {"title":"%s","reason":"balanced family dinner","mealTypes":["DINNER"],"recipes":[{"mealType":"DINNER","name":"%s","servingCount":2,"reason":"simple pantry meal"}],"costEstimate":12.50}
                """.formatted(title, dishName);
    }

    private SmartLifecycle newRecommendationRunner(NutritionAiRecommendationScheduler scheduler,
                                                   long initialDelayMillis, long fixedDelayMillis) throws Exception {
        Constructor<?> constructor = Class.forName(
                        "top.egon.mario.nutrition.service.ai.NutritionAiRecommendationRunner")
                .getConstructor(NutritionAiRecommendationScheduler.class, long.class, long.class);
        return (SmartLifecycle) constructor.newInstance(scheduler, initialDelayMillis, fixedDelayMillis);
    }

    @TestConfiguration
    static class NutritionAiTestConfiguration {

        @Bean
        @Primary
        FakeNutritionAiModelClient fakeNutritionAiModelClient() {
            return new FakeNutritionAiModelClient();
        }
    }

    static class FakeNutritionAiModelClient implements NutritionAiModelClient {

        private final Queue<Object> responses = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        void addResponse(String rawJson) {
            responses.add(rawJson);
        }

        void addFailure(RuntimeException error) {
            responses.add(error);
        }

        void reset() {
            responses.clear();
            calls.set(0);
        }

        int calls() {
            return calls.get();
        }

        @Override
        public String generateMenu(NutritionAiModelRequest request) {
            calls.incrementAndGet();
            Object response = responses.remove();
            if (response instanceof RuntimeException error) {
                throw error;
            }
            return (String) response;
        }
    }
}
