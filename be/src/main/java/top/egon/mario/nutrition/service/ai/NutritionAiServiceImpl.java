package top.egon.mario.nutrition.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
import top.egon.mario.nutrition.service.NutritionException;
import top.egon.mario.nutrition.service.access.NutritionAccessService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default AI recommendation job service.
 */
@Service
@RequiredArgsConstructor
@Validated
public class NutritionAiServiceImpl implements NutritionAiService {

    private static final TypeReference<List<NutritionMealType>> MEAL_TYPE_LIST_TYPE = new TypeReference<>() {
    };
    private static final List<NutritionMealType> DEFAULT_MEAL_TYPES = List.of(NutritionMealType.DINNER);
    private static final List<NutritionAiJobStatus> ACTIVE_SCHEDULED_STATUSES = List.of(
            NutritionAiJobStatus.PENDING,
            NutritionAiJobStatus.RUNNING,
            NutritionAiJobStatus.SUCCEEDED
    );

    private final NutritionFamilyRepository familyRepository;
    private final NutritionAiRecommendationJobRepository aiJobRepository;
    private final NutritionAiRecommendationRepository aiRecommendationRepository;
    private final NutritionMealPlanRepository mealPlanRepository;
    private final NutritionMealPlanItemRepository mealPlanItemRepository;
    private final NutritionAccessService accessService;
    private final NutritionAiModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public NutritionAiRecommendationJobResponse generateManualRecommendation(@NotNull Long familyId,
                                                                             @NotNull LocalDate plannedDate,
                                                                             List<NutritionMealType> targetMealTypes,
                                                                             Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireManageFamily(userId, familyId);
        Long jobId = transactionTemplate.execute(status -> {
            NutritionFamilyPo family = getActiveFamily(familyId);
            List<NutritionMealType> mealTypes = resolveMealTypes(family, targetMealTypes);
            NutritionAiTriggerType triggerType = aiRecommendationRepository
                    .existsByFamilyIdAndRecommendationDateAndDeletedFalse(familyId, plannedDate)
                    ? NutritionAiTriggerType.REGENERATE
                    : NutritionAiTriggerType.MANUAL;
            return createJob(family, plannedDate, mealTypes, triggerType, userId).getId();
        });
        return runJob(jobId);
    }

    @Override
    public NutritionAiRecommendationJobResponse generateScheduledRecommendation(@NotNull Long familyId,
                                                                                @NotNull LocalDate plannedDate) {
        ScheduledJobDecision decision = transactionTemplate.execute(status -> {
            NutritionFamilyPo family = getLockedActiveFamily(familyId);
            if (!family.isAiEnabled()) {
                throw new NutritionException("NUTRITION_AI_DISABLED", "nutrition AI generation is disabled");
            }
            NutritionAiRecommendationJobPo existingJob = aiJobRepository
                    .findFirstByFamilyIdAndTriggerTypeAndPlannedDateAndStatusInAndDeletedFalseOrderByIdDesc(
                            familyId, NutritionAiTriggerType.SCHEDULED, plannedDate, ACTIVE_SCHEDULED_STATUSES)
                    .orElse(null);
            if (existingJob != null) {
                return new ScheduledJobDecision(null, toExistingScheduledJobResponse(existingJob));
            }
            List<NutritionMealType> mealTypes = resolveMealTypes(family, null);
            Long jobId = createJob(family, plannedDate, mealTypes, NutritionAiTriggerType.SCHEDULED, null).getId();
            return new ScheduledJobDecision(jobId, null);
        });
        if (decision.existingResponse() != null) {
            return decision.existingResponse();
        }
        return runJob(decision.jobId());
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

    private NutritionAiRecommendationJobResponse runJob(Long jobId) {
        NutritionAiModelRequest request = transactionTemplate.execute(status -> markRunning(jobId));
        String rawOutput = null;
        try {
            rawOutput = modelClient.generateMenu(request);
            String finalRawOutput = rawOutput;
            NutritionAiMenuDraft draft = normalizeDraft(finalRawOutput);
            return transactionTemplate.execute(status -> completeSucceeded(jobId, finalRawOutput, draft));
        } catch (RuntimeException error) {
            String finalRawOutput = rawOutput;
            return transactionTemplate.execute(status -> completeFailed(jobId, finalRawOutput, error));
        }
    }

    private NutritionAiRecommendationJobPo createJob(NutritionFamilyPo family, LocalDate plannedDate,
                                                     List<NutritionMealType> mealTypes,
                                                     NutritionAiTriggerType triggerType, Long requestedBy) {
        NutritionAiRecommendationJobPo job = new NutritionAiRecommendationJobPo();
        job.setFamilyId(family.getId());
        job.setTriggerType(triggerType);
        job.setStatus(NutritionAiJobStatus.PENDING);
        job.setRequestedBy(requestedBy);
        job.setPlannedDate(plannedDate);
        job.setTargetMealTypes(writeJson(mealTypes));
        job.setInputSnapshot(writeInputSnapshot(family, plannedDate, mealTypes, triggerType, requestedBy));
        job.setOutputSnapshot("{}");
        job.setMetadataJson("{}");
        return aiJobRepository.saveAndFlush(job);
    }

    private NutritionAiModelRequest markRunning(Long jobId) {
        NutritionAiRecommendationJobPo job = getJob(jobId);
        NutritionFamilyPo family = getActiveFamily(job.getFamilyId());
        job.setStatus(NutritionAiJobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setErrorMessage(null);
        aiJobRepository.saveAndFlush(job);
        return new NutritionAiModelRequest(job.getId(), family.getId(), family.getName(), job.getPlannedDate(),
                readMealTypes(job.getTargetMealTypes()), job.getInputSnapshot(), job.getRequestedBy());
    }

    private NutritionAiRecommendationJobResponse completeSucceeded(Long jobId, String rawOutput,
                                                                    NutritionAiMenuDraft draft) {
        NutritionAiRecommendationJobPo job = getJob(jobId);
        job.setOutputSnapshot(writeRawOutputSnapshot(rawOutput));
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(null);
        job.setStatus(NutritionAiJobStatus.SUCCEEDED);

        NutritionAiRecommendationPo recommendation = new NutritionAiRecommendationPo();
        recommendation.setFamilyId(job.getFamilyId());
        recommendation.setAiJobId(job.getId());
        recommendation.setRecommendationDate(job.getPlannedDate());
        recommendation.setTitle(clip(draft.title().trim(), 128));
        recommendation.setReason(safeText(draft.reason()));
        recommendation.setMealTypes(writeJson(draft.mealTypes()));
        recommendation.setInputSnapshot(job.getInputSnapshot());
        recommendation.setOutputSnapshot(writeRawOutputSnapshot(rawOutput));
        recommendation.setRiskSummary("{}");
        recommendation.setCostEstimate(draft.costEstimate());
        recommendation.setStatus(NutritionStatus.ACTIVE);
        recommendation.setMetadataJson(writeJson(Map.of("normalizedOutput", draft)));
        NutritionAiRecommendationPo savedRecommendation = aiRecommendationRepository.saveAndFlush(recommendation);

        NutritionMealPlanPo mealPlan = new NutritionMealPlanPo();
        mealPlan.setFamilyId(job.getFamilyId());
        mealPlan.setAiRecommendationId(savedRecommendation.getId());
        mealPlan.setPlanDate(job.getPlannedDate());
        mealPlan.setStatus(NutritionMealPlanStatus.PENDING_REVIEW);
        mealPlan.setTitle(clip(draft.title().trim(), 128));
        mealPlan.setPublishedAt(null);
        mealPlan.setConfirmedMemberCount(0);
        mealPlan.setEstimatedCost(draft.costEstimate());
        mealPlan.setNutritionSnapshot(writeJson(Map.of("source", "AI_RECOMMENDATION", "draft", draft)));
        mealPlan.setMetadataJson(writeJson(Map.of("aiJobId", job.getId())));
        NutritionMealPlanPo savedMealPlan = mealPlanRepository.saveAndFlush(mealPlan);

        int sortOrder = 0;
        for (NutritionAiRecipeDraft recipe : draft.recipes()) {
            NutritionMealPlanItemPo item = new NutritionMealPlanItemPo();
            item.setFamilyId(job.getFamilyId());
            item.setMealPlanId(savedMealPlan.getId());
            item.setMealType(recipe.mealType());
            item.setDishName(clip(recipe.name().trim(), 128));
            item.setServingCount(recipe.servingCount() == null ? BigDecimal.ONE : recipe.servingCount());
            item.setSortOrder(sortOrder++);
            item.setNutritionSnapshot(writeJson(Map.of("reason", safeText(recipe.reason()))));
            item.setCostSnapshot("{}");
            item.setStatus(NutritionStatus.ACTIVE);
            mealPlanItemRepository.save(item);
        }

        job.setMetadataJson(writeJson(Map.of(
                "normalizedOutput", draft,
                "recommendationId", savedRecommendation.getId(),
                "mealPlanId", savedMealPlan.getId()
        )));
        aiJobRepository.saveAndFlush(job);
        return toJobResponse(job, savedRecommendation.getId(), savedMealPlan.getId());
    }

    private NutritionAiRecommendationJobResponse completeFailed(Long jobId, String rawOutput, RuntimeException error) {
        NutritionAiRecommendationJobPo job = getJob(jobId);
        job.setOutputSnapshot(StringUtils.hasText(rawOutput) ? writeRawOutputSnapshot(rawOutput) : "{}");
        job.setCompletedAt(Instant.now());
        job.setErrorMessage(errorText(error));
        job.setStatus(NutritionAiJobStatus.FAILED);
        job.setMetadataJson(writeJson(Map.of("failureType", error == null ? "unknown" : error.getClass().getName())));
        NutritionAiRecommendationJobPo saved = aiJobRepository.saveAndFlush(job);
        return toJobResponse(saved, null, null);
    }

    private NutritionAiMenuDraft normalizeDraft(String rawOutput) {
        NutritionAiMenuDraft draft;
        try {
            draft = objectMapper.readValue(rawOutput, NutritionAiMenuDraft.class);
        } catch (JsonProcessingException e) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation output is invalid JSON");
        }
        if (draft == null) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation output is empty");
        }
        if (!StringUtils.hasText(draft.title())) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation title is required");
        }
        if (draft.mealTypes().isEmpty()) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation meal types are required");
        }
        if (draft.recipes().isEmpty()) {
            throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation recipes are required");
        }
        for (NutritionAiRecipeDraft recipe : draft.recipes()) {
            if (recipe.mealType() == null || !StringUtils.hasText(recipe.name())) {
                throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID", "AI recommendation recipe is invalid");
            }
            if (!draft.mealTypes().contains(recipe.mealType())) {
                throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID",
                        "AI recommendation recipe meal type is not requested");
            }
            if (recipe.servingCount() != null && recipe.servingCount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new NutritionException("NUTRITION_AI_OUTPUT_INVALID",
                        "AI recommendation recipe serving count is invalid");
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

    private NutritionAiRecommendationJobPo getJob(Long jobId) {
        return aiJobRepository.findById(jobId)
                .filter(job -> !job.isDeleted())
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

    private String writeInputSnapshot(NutritionFamilyPo family, LocalDate plannedDate,
                                      List<NutritionMealType> mealTypes, NutritionAiTriggerType triggerType,
                                      Long requestedBy) {
        Map<String, Object> familySnapshot = new LinkedHashMap<>();
        familySnapshot.put("id", family.getId());
        familySnapshot.put("name", family.getName());
        familySnapshot.put("region", family.getRegion());
        familySnapshot.put("currency", family.getCurrency());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("family", familySnapshot);
        input.put("plannedDate", plannedDate);
        input.put("mealTypes", mealTypes);
        input.put("triggerType", triggerType);
        input.put("requestedBy", requestedBy);
        return writeJson(input);
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

    private NutritionAiRecommendationJobResponse toJobResponse(NutritionAiRecommendationJobPo job,
                                                               Long recommendationId, Long mealPlanId) {
        return new NutritionAiRecommendationJobResponse(job.getId(), job.getFamilyId(), job.getTriggerType(),
                job.getStatus(), job.getRequestedBy(), job.getPlannedDate(), readMealTypes(job.getTargetMealTypes()),
                recommendationId, mealPlanId, job.getErrorMessage(), job.getStartedAt(), job.getCompletedAt(),
                job.getCreatedAt(), job.getUpdatedAt());
    }

    private NutritionAiRecommendationJobResponse toExistingScheduledJobResponse(NutritionAiRecommendationJobPo job) {
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

    private NutritionAiRecommendationResponse toRecommendationResponse(NutritionAiRecommendationPo recommendation,
                                                                       Long mealPlanId) {
        return new NutritionAiRecommendationResponse(recommendation.getId(), recommendation.getFamilyId(),
                recommendation.getAiJobId(), recommendation.getRecommendationDate(), recommendation.getTitle(),
                recommendation.getReason(), readMealTypes(recommendation.getMealTypes()),
                recommendation.getCostEstimate(), recommendation.getStatus(), mealPlanId,
                recommendation.getCreatedAt(), recommendation.getUpdatedAt());
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String errorText(RuntimeException error) {
        if (error instanceof NutritionException nutritionException
                && StringUtils.hasText(nutritionException.getCode())) {
            String message = StringUtils.hasText(error.getMessage())
                    ? error.getMessage().trim()
                    : error.getClass().getSimpleName();
            return "AI recommendation generation failed [" + nutritionException.getCode() + "]: " + message;
        }
        if (error == null || !StringUtils.hasText(error.getMessage())) {
            String type = error == null ? "unknown" : error.getClass().getSimpleName();
            return "AI recommendation generation failed: " + type;
        }
        return "AI recommendation generation failed: " + error.getMessage().trim();
    }

    private record ScheduledJobDecision(Long jobId, NutritionAiRecommendationJobResponse existingResponse) {
    }
}
