package top.egon.mario.nutrition.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationResponse;
import top.egon.mario.nutrition.po.NutritionAiRecommendationJobPo;
import top.egon.mario.nutrition.po.NutritionAiRecommendationPo;
import top.egon.mario.nutrition.po.NutritionFamilyPo;
import top.egon.mario.nutrition.po.NutritionMealPlanItemPo;
import top.egon.mario.nutrition.po.NutritionMealPlanPo;
import top.egon.mario.nutrition.po.enums.NutritionAiJobStatus;
import top.egon.mario.nutrition.po.enums.NutritionAiTriggerType;
import top.egon.mario.nutrition.po.enums.NutritionMealPlanStatus;
import top.egon.mario.nutrition.po.enums.NutritionMealType;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationJobRepository;
import top.egon.mario.nutrition.repository.NutritionAiRecommendationRepository;
import top.egon.mario.nutrition.repository.NutritionFamilyRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanItemRepository;
import top.egon.mario.nutrition.repository.NutritionMealPlanRepository;
import top.egon.mario.nutrition.service.MealValidationResult;
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.NutritionMealValidationService;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default asynchronous AI recommendation job service.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionAiServiceImpl implements NutritionAiService {

    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final List<NutritionMealType> DEFAULT_MEAL_TYPES = List.of(NutritionMealType.DINNER);
    private static final List<NutritionAiJobStatus> ACTIVE_SCHEDULED_STATUSES = List.of(
            NutritionAiJobStatus.PENDING,
            NutritionAiJobStatus.RUNNING,
            NutritionAiJobStatus.SUCCEEDED
    );
    private static final int MAX_RETRIES = 3;
    private static final int MAX_RUN_BATCH_SIZE = 100;

    private final NutritionFamilyRepository familyRepository;
    private final NutritionAiRecommendationJobRepository aiJobRepository;
    private final NutritionAiRecommendationRepository aiRecommendationRepository;
    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionAccessService accessService;
    private final NutritionRecommendationContextService contextService;
    private final NutritionAiRecipeMaterializationService materializationService;
    private final NutritionMealValidationService mealValidationService;
    private final NutritionAiModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public NutritionAiRecommendationJobResponse generateManualRecommendation(@NotNull Long familyId,
                                                                             @NotNull LocalDate plannedDate,
                                                                             List<NutritionMealType> targetMealTypes,
                                                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireCookFamily(userId, familyId);
        return transactionTemplate.execute(status -> {
            NutritionFamilyPo family = getActiveFamily(familyId);
            List<NutritionMealType> mealTypes = resolveMealTypes(family, targetMealTypes);
            NutritionAiTriggerType triggerType = aiRecommendationRepository
                    .existsByFamilyIdAndRecommendationDateAndDeletedFalse(familyId, plannedDate)
                    ? NutritionAiTriggerType.REGENERATE
                    : NutritionAiTriggerType.MANUAL;
            NutritionAiRecommendationJobPo job = createJob(
                    family, plannedDate, mealTypes, triggerType, userId);
            return toJobResponse(job, null, null);
        });
    }

    @Override
    public NutritionAiRecommendationJobResponse generateScheduledRecommendation(@NotNull Long familyId,
                                                                                @NotNull LocalDate plannedDate) {
        return transactionTemplate.execute(status -> {
            NutritionFamilyPo family = getLockedActiveFamily(familyId);
            if (!family.isAiEnabled()) {
                throw new NutritionException("NUTRITION_AI_DISABLED", "nutrition AI generation is disabled");
            }
            NutritionAiRecommendationJobPo existingJob = aiJobRepository
                    .findFirstByFamilyIdAndTriggerTypeAndPlannedDateAndStatusInAndDeletedFalseOrderByIdDesc(
                            familyId, NutritionAiTriggerType.SCHEDULED, plannedDate, ACTIVE_SCHEDULED_STATUSES)
                    .orElse(null);
            if (existingJob != null) {
                return responseWithPersistedIds(existingJob);
            }
            List<NutritionMealType> mealTypes = resolveMealTypes(family, null);
            NutritionAiRecommendationJobPo job = createJob(
                    family, plannedDate, mealTypes, NutritionAiTriggerType.SCHEDULED, null);
            return toJobResponse(job, null, null);
        });
    }

    @Override
    public int runPendingJobs(int limit) {
        int batchSize = Math.min(Math.max(limit, 0), MAX_RUN_BATCH_SIZE);
        if (batchSize == 0) {
            return 0;
        }
        transactionTemplate.executeWithoutResult(status -> requeueDueFailedJobs());
        int processed = 0;
        while (processed < batchSize) {
            ClaimedJob claimed = transactionTemplate.execute(status -> claimOldestPendingJob());
            if (claimed == null) {
                break;
            }
            String rawOutput = null;
            try {
                rawOutput = modelClient.generateMenu(claimed.request());
                NutritionAiMenuDraft draft = normalizeDraft(rawOutput, claimed.request().mealTypes());
                String finalRawOutput = rawOutput;
                transactionTemplate.executeWithoutResult(status -> completeSucceeded(
                        claimed.jobId(), finalRawOutput, draft));
            } catch (RuntimeException error) {
                String finalRawOutput = rawOutput;
                transactionTemplate.executeWithoutResult(status -> completeFailed(
                        claimed.jobId(), finalRawOutput, error));
            }
            processed++;
        }
        return processed;
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionAiRecommendationJobResponse getJob(@NotNull Long familyId, @NotNull Long jobId,
                                                       Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionAiRecommendationJobPo job = aiJobRepository.findByIdAndFamilyIdAndDeletedFalse(jobId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_AI_JOB_NOT_FOUND", "nutrition AI recommendation job not found"));
        return responseWithPersistedIds(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutritionAiRecommendationJobResponse> listJobs(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return aiJobRepository.findTop20ByFamilyIdAndDeletedFalseOrderByIdDesc(familyId).stream()
                .map(this::responseWithPersistedIds)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NutritionAiRecommendationResponse> listRecommendations(@NotNull Long familyId, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        List<NutritionAiRecommendationPo> recommendations = aiRecommendationRepository
                .findByFamilyIdAndDeletedFalseOrderByRecommendationDateDescIdDesc(familyId);
        if (recommendations.isEmpty()) {
            return List.of();
        }
        List<Long> recommendationIds = recommendations.stream().map(NutritionAiRecommendationPo::getId).toList();
        Map<Long, Long> mealPlanIds = mealPlanRepository.findByAiRecommendationIdInAndDeletedFalse(recommendationIds)
                .stream()
                .collect(Collectors.toMap(NutritionMealPlanPo::getAiRecommendationId, NutritionMealPlanPo::getId,
                        (left, right) -> left));
        return recommendations.stream()
                .map(recommendation -> toRecommendationResponse(recommendation,
                        mealPlanIds.get(recommendation.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NutritionAiRecommendationResponse getRecommendation(@NotNull Long familyId, @NotNull Long recommendationId,
                                                               Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        NutritionAiRecommendationPo recommendation = aiRecommendationRepository
                .findByIdAndFamilyIdAndDeletedFalse(recommendationId, familyId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_AI_RECOMMENDATION_NOT_FOUND", "nutrition AI recommendation not found"));
        Long mealPlanId = mealPlanRepository.findFirstByAiRecommendationIdAndDeletedFalseOrderByIdDesc(
                        recommendation.getId())
                .map(NutritionMealPlanPo::getId)
                .orElse(null);
        return toRecommendationResponse(recommendation, mealPlanId);
    }

    private NutritionAiRecommendationJobPo createJob(NutritionFamilyPo family, LocalDate plannedDate,
                                                     List<NutritionMealType> mealTypes,
                                                     NutritionAiTriggerType triggerType, Long requestedBy) {
        NutritionRecommendationContext context = contextService.build(
                family.getId(), plannedDate, mealTypes, requestedBy, triggerType);
        NutritionAiRecommendationJobPo job = new NutritionAiRecommendationJobPo();
        job.setFamilyId(family.getId());
        job.setTriggerType(triggerType);
        job.setStatus(NutritionAiJobStatus.PENDING);
        job.setRequestedBy(requestedBy);
        job.setPlannedDate(plannedDate);
        job.setTargetMealTypes(writeJson(mealTypes));
        job.setInputSnapshot(writeJson(context));
        job.setOutputSnapshot("{}");
        job.setMetadataJson(writeJson(Map.of(
                "retryCount", 0,
                "maxRetries", MAX_RETRIES
        )));
        return aiJobRepository.saveAndFlush(job);
    }

    private ClaimedJob claimOldestPendingJob() {
        NutritionAiRecommendationJobPo job = aiJobRepository.findLockedByStatusOrderByIdAsc(
                        NutritionAiJobStatus.PENDING, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElse(null);
        if (job == null) {
            return null;
        }
        NutritionFamilyPo family = getActiveFamily(job.getFamilyId());
        job.setStatus(NutritionAiJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setCompletedAt(null);
        job.setErrorMessage(null);
        aiJobRepository.saveAndFlush(job);
        return new ClaimedJob(job.getId(), new NutritionAiModelRequest(
                job.getId(), family.getId(), family.getName(), job.getPlannedDate(),
                readMealTypes(job.getTargetMealTypes()), job.getInputSnapshot(), job.getRequestedBy()));
    }

    private void completeSucceeded(Long jobId, String rawOutput, NutritionAiMenuDraft draft) {
        NutritionAiRecommendationJobPo job = getLockedJob(jobId);
        if (job.getStatus() != NutritionAiJobStatus.RUNNING) {
            throw new NutritionException("NUTRITION_AI_JOB_STATE_INVALID", "nutrition AI job is not running");
        }
        NutritionFamilyPo family = getActiveFamily(job.getFamilyId());
        Long actorId = job.getRequestedBy() == null ? family.getOwnerUserId() : job.getRequestedBy();
        List<MaterializedNutritionRecipe> materializedRecipes = materializationService.materialize(
                family.getId(), draft, actorId);

        NutritionAiRecommendationPo recommendation = new NutritionAiRecommendationPo();
        recommendation.setFamilyId(job.getFamilyId());
        recommendation.setAiJobId(job.getId());
        recommendation.setRecommendationDate(job.getPlannedDate());
        recommendation.setTitle(draft.title().trim());
        recommendation.setReason(safeText(draft.reason()));
        recommendation.setMealTypes(writeJson(draft.mealTypes()));
        recommendation.setInputSnapshot(job.getInputSnapshot());
        recommendation.setOutputSnapshot(writeRawOutputSnapshot(rawOutput));
        recommendation.setRiskSummary("{}");
        recommendation.setCostEstimate(draft.costEstimate());
        recommendation.setStatus(NutritionStatus.ACTIVE);
        recommendation.setMetadataJson(writeJson(Map.of(
                "aiExplanation", safeText(draft.reason()),
                "normalizedOutput", draft
        )));
        NutritionAiRecommendationPo savedRecommendation = aiRecommendationRepository.saveAndFlush(recommendation);

        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(job.getFamilyId());
        mealPlan.setAiRecommendationId(savedRecommendation.getId());
        mealPlan.setPlanDate(job.getPlannedDate());
        mealPlan.setStatus(NutritionMealPlanStatus.PENDING_REVIEW);
        mealPlan.setTitle(draft.title().trim());
        mealPlan.setPublishedAt(null);
        mealPlan.setConfirmedMemberCount(0);
        mealPlan.setEstimatedCost(draft.costEstimate());
        mealPlan.setNutritionSnapshot("{}");
        mealPlan.setMetadataJson(writeJson(Map.of("aiJobId", job.getId())));
        NutritionMealPlanPo savedMealPlan = mealPlanRepository.saveAndFlush(mealPlan);

        int sortOrder = 0;
        for (MaterializedNutritionRecipe recipe : materializedRecipes) {
            NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
            item.setFamilyId(job.getFamilyId());
            item.setMealPlanId(savedMealPlan.getId());
            item.setMealType(recipe.mealType());
            item.setRecipeId(recipe.recipeId());
            item.setDishName(recipe.dishName());
            item.setServingCount(recipe.servingCount());
            item.setSortOrder(sortOrder++);
            item.setNutritionSnapshot(writeJson(recipe.nutrients()));
            item.setCostSnapshot(recipe.estimatedCost() == null
                    ? "{}" : writeJson(Map.of("estimatedCost", recipe.estimatedCost())));
            item.setStatus(NutritionStatus.ACTIVE);
            item.setMetadataJson("{}");
            mealPlanItemRepository.save(item);
        }
        mealPlanItemRepository.flush();

        MealValidationResult validation = mealValidationService.validateAndPersist(
                family.getId(), savedMealPlan.getId(), actorId);
        savedRecommendation.setRiskSummary(writeJson(validation.risks()));
        savedRecommendation.setCostEstimate(validation.estimatedCost());
        aiRecommendationRepository.save(savedRecommendation);

        job.setOutputSnapshot(writeRawOutputSnapshot(rawOutput));
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(null);
        job.setStatus(NutritionAiJobStatus.SUCCEEDED);
        Map<String, Object> metadata = readMetadata(job.getMetadataJson());
        metadata.put("normalizedOutput", draft);
        metadata.put("recommendationId", savedRecommendation.getId());
        metadata.put("mealPlanId", savedMealPlan.getId());
        metadata.put("publishable", validation.publishable());
        metadata.remove("nextRetryAt");
        job.setMetadataJson(writeJson(metadata));
        aiJobRepository.saveAndFlush(job);
    }

    private void completeFailed(Long jobId, String rawOutput, RuntimeException error) {
        NutritionAiRecommendationJobPo job = getLockedJob(jobId);
        Map<String, Object> metadata = readMetadata(job.getMetadataJson());
        int retryCount = intValue(metadata.get("retryCount"), 0) + 1;
        Instant nextRetryAt = Instant.now().plus(retryDelay(retryCount));
        boolean retryable = retryable(error);
        metadata.put("retryCount", retryCount);
        metadata.put("maxRetries", MAX_RETRIES);
        metadata.put("retryable", retryable);
        metadata.put("errorCode", errorCode(error));
        metadata.put("failureType", error == null ? "unknown" : error.getClass().getName());
        if (retryable && retryCount < MAX_RETRIES) {
            metadata.put("nextRetryAt", nextRetryAt.toString());
        } else {
            metadata.remove("nextRetryAt");
        }
        job.setOutputSnapshot(StringUtils.hasText(rawOutput) ? writeRawOutputSnapshot(rawOutput) : "{}");
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(errorText(error));
        job.setStatus(NutritionAiJobStatus.FAILED);
        job.setMetadataJson(writeJson(metadata));
        aiJobRepository.saveAndFlush(job);
    }

    private boolean retryable(RuntimeException error) {
        return !(error instanceof NutritionException nutritionException)
                || "NUTRITION_AI_EMPTY_OUTPUT".equals(nutritionException.getCode());
    }

    private void requeueDueFailedJobs() {
        Instant now = Instant.now();
        for (NutritionAiRecommendationJobPo job : aiJobRepository
                .findByStatusAndDeletedFalseOrderByIdAsc(NutritionAiJobStatus.FAILED)) {
            Map<String, Object> metadata = readMetadata(job.getMetadataJson());
            int retryCount = intValue(metadata.get("retryCount"), 0);
            int maxRetries = intValue(metadata.get("maxRetries"), MAX_RETRIES);
            Instant nextRetryAt = instantValue(metadata.get("nextRetryAt"));
            if (retryCount >= maxRetries || nextRetryAt == null || nextRetryAt.isAfter(now)) {
                continue;
            }
            job.setStatus(NutritionAiJobStatus.PENDING);
            job.setCompletedAt(null);
            metadata.remove("nextRetryAt");
            metadata.put("requeuedAt", now.toString());
            job.setMetadataJson(writeJson(metadata));
            aiJobRepository.save(job);
        }
    }

    private NutritionAiMenuDraft normalizeDraft(String rawOutput, List<NutritionMealType> requestedMealTypes) {
        NutritionAiMenuDraft draft;
        try {
            draft = objectMapper.readValue(rawOutput, NutritionAiMenuDraft.class);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation output is invalid JSON");
        }
        if (draft == null) {
            throw invalidOutput("AI recommendation output is empty");
        }
        if (!StringUtils.hasText(draft.title()) || draft.title().trim().length() > 128) {
            throw invalidOutput("AI recommendation title is invalid");
        }
        if (draft.mealTypes().isEmpty() || !requestedMealTypes.containsAll(draft.mealTypes())) {
            throw invalidOutput("AI recommendation meal types are invalid");
        }
        if (draft.recipes().isEmpty()) {
            throw invalidOutput("AI recommendation recipes are required");
        }
        for (NutritionAiRecipeDraft recipe : draft.recipes()) {
            if (recipe == null || recipe.mealType() == null
                    || !draft.mealTypes().contains(recipe.mealType())
                    || !requestedMealTypes.contains(recipe.mealType())) {
                throw invalidOutput("AI recommendation recipe meal type is not requested");
            }
            boolean existing = recipe.existingRecipeId() != null;
            boolean generated = StringUtils.hasText(recipe.name()) || !recipe.ingredients().isEmpty()
                    || !recipe.steps().isEmpty();
            if (existing == generated) {
                throw invalidOutput("AI recipe must contain exactly one existing id or generated body");
            }
            if (generated && recipe.name().trim().length() > 128) {
                throw invalidOutput("AI recommendation recipe name is invalid");
            }
            if (recipe.servingCount() != null && recipe.servingCount().compareTo(BigDecimal.ZERO) <= 0) {
                throw invalidOutput("AI recommendation recipe serving count is invalid");
            }
        }
        return draft;
    }

    private NutritionFamilyPo getActiveFamily(Long familyId) {
        return familyRepository.findByIdAndDeletedFalse(familyId)
                .filter(family -> NutritionStatus.ACTIVE == family.getStatus())
                .orElseThrow(() -> new NutritionException("NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
    }

    private NutritionFamilyPo getLockedActiveFamily(Long familyId) {
        return familyRepository.findLockedByIdAndDeletedFalse(familyId)
                .filter(family -> NutritionStatus.ACTIVE == family.getStatus())
                .orElseThrow(() -> new NutritionException("NUTRITION_FAMILY_NOT_FOUND", "nutrition family not found"));
    }

    private NutritionAiRecommendationJobPo getLockedJob(Long jobId) {
        return aiJobRepository.findLockedByIdAndDeletedFalse(jobId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_AI_JOB_NOT_FOUND", "nutrition AI recommendation job not found"));
    }

    private List<NutritionMealType> resolveMealTypes(NutritionFamilyPo family,
                                                     List<NutritionMealType> requestedMealTypes) {
        List<NutritionMealType> mealTypes = normalizeMealTypes(requestedMealTypes);
        if (!mealTypes.isEmpty()) {
            return mealTypes;
        }
        mealTypes = normalizeMealTypes(readMealTypes(family.getDefaultMealTypes()));
        return mealTypes.isEmpty() ? DEFAULT_MEAL_TYPES : mealTypes;
    }

    private List<NutritionMealType> normalizeMealTypes(List<NutritionMealType> mealTypes) {
        return mealTypes == null ? List.of() : mealTypes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private NutritionAiRecommendationJobResponse responseWithPersistedIds(NutritionAiRecommendationJobPo job) {
        if (job.getStatus() != NutritionAiJobStatus.SUCCEEDED) {
            return toJobResponse(job, null, null);
        }
        NutritionAiRecommendationPo recommendation = aiRecommendationRepository
                .findFirstByAiJobIdAndDeletedFalseOrderByIdDesc(job.getId())
                .orElse(null);
        if (recommendation == null) {
            return toJobResponse(job, null, null);
        }
        Long mealPlanId = mealPlanRepository.findFirstByAiRecommendationIdAndDeletedFalseOrderByIdDesc(
                        recommendation.getId())
                .map(NutritionMealPlanPo::getId)
                .orElse(null);
        return toJobResponse(job, recommendation.getId(), mealPlanId);
    }

    private NutritionAiRecommendationJobResponse toJobResponse(NutritionAiRecommendationJobPo job,
                                                               Long recommendationId, Long mealPlanId) {
        return new NutritionAiRecommendationJobResponse(job.getId(), job.getFamilyId(), job.getTriggerType(),
                job.getStatus(), job.getRequestedBy(), job.getPlannedDate(), readMealTypes(job.getTargetMealTypes()),
                recommendationId, mealPlanId, job.getErrorMessage(), job.getStartedAt(), job.getCompletedAt(),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    private NutritionAiRecommendationResponse toRecommendationResponse(NutritionAiRecommendationPo recommendation,
                                                                       Long mealPlanId) {
        return new NutritionAiRecommendationResponse(recommendation.getId(), recommendation.getFamilyId(),
                recommendation.getAiJobId(), recommendation.getRecommendationDate(), recommendation.getTitle(),
                recommendation.getReason(), readMealTypes(recommendation.getMealTypes()),
                recommendation.getCostEstimate(), recommendation.getStatus(), mealPlanId,
                recommendation.getCreatedAt(), recommendation.getUpdatedAt());
    }

    private Map<String, Object> readMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, METADATA_TYPE));
        } catch (JsonProcessingException error) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition AI job metadata is invalid");
        }
    }

    private List<NutritionMealType> readMealTypes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, MEAL_TYPE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition meal type JSON is invalid");
        }
    }

    private String writeRawOutputSnapshot(String rawOutput) {
        return writeJson(Map.of("raw", rawOutput));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_JSON_INVALID", "nutrition JSON value is invalid");
        }
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private int intValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private Instant instantValue(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Duration retryDelay(int retryCount) {
        long seconds = 60L * (1L << Math.min(Math.max(retryCount - 1, 0), 4));
        return Duration.ofSeconds(seconds);
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof NutritionException nutritionException
                && StringUtils.hasText(nutritionException.getCode())) {
            return nutritionException.getCode();
        }
        return "NUTRITION_AI_GENERATION_FAILED";
    }

    private String errorText(RuntimeException error) {
        String code = errorCode(error);
        String message = error == null || !StringUtils.hasText(error.getMessage())
                ? (error == null ? "unknown" : error.getClass().getSimpleName())
                : error.getMessage().trim();
        return "AI recommendation generation failed [" + code + "]: " + message;
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private NutritionException invalidOutput(String message) {
        return new NutritionException("NUTRITION_AI_OUTPUT_INVALID", message);
    }

    private record ClaimedJob(Long jobId, NutritionAiModelRequest request) {
    }
}
