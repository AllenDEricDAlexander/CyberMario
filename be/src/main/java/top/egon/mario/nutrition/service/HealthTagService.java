package top.egon.mario.nutrition.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import top.egon.mario.nutrition.dto.request.UpsertHealthTagRequest;
import top.egon.mario.nutrition.dto.response.HealthTagResponse;
import top.egon.mario.nutrition.po.NutritionHealthTagPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;
import top.egon.mario.nutrition.repository.NutritionHealthTagRepository;
import top.egon.mario.nutrition.service.access.NutritionAccessService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;
import java.util.Locale;

/**
 * Application service for platform health-tag dictionaries and family read access.
 */
@Service
@RequiredArgsConstructor
@Validated
public class HealthTagService {

    private final NutritionHealthTagRepository healthTagRepository;
    private final NutritionAccessService accessService;

    @Transactional(readOnly = true)
    public List<HealthTagResponse> listTags(String tagType, boolean activeOnly, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
        return findTags(tagType, activeOnly);
    }

    @Transactional(readOnly = true)
    public List<HealthTagResponse> listFamilyTags(@NotNull Long familyId, String tagType, Long actorId) {
        Long userId = requireActor(actorId);
        accessService.requireReadFamily(userId, familyId);
        return findTags(tagType, true);
    }

    @Transactional
    public HealthTagResponse createTag(@Valid @NotNull UpsertHealthTagRequest request,
                                       RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
        String tagType = normalizeCode(request.tagType());
        String tagCode = normalizeCode(request.tagCode());
        healthTagRepository.findByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndDeletedFalse(tagType, tagCode)
                .ifPresent(existing -> {
                    throw duplicate();
                });
        NutritionHealthTagPo tag = new NutritionHealthTagPo();
        apply(tag, request, tagType, tagCode);
        tag.setStatus(NutritionStatus.ACTIVE);
        return toResponse(healthTagRepository.save(tag));
    }

    @Transactional
    public HealthTagResponse updateTag(@NotNull Long tagId,
                                       @Valid @NotNull UpsertHealthTagRequest request,
                                       RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
        NutritionHealthTagPo tag = getTag(tagId);
        String tagType = normalizeCode(request.tagType());
        String tagCode = normalizeCode(request.tagCode());
        if (healthTagRepository.existsByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndIdNotAndDeletedFalse(
                tagType, tagCode, tagId)) {
            throw duplicate();
        }
        apply(tag, request, tagType, tagCode);
        return toResponse(healthTagRepository.save(tag));
    }

    @Transactional
    public HealthTagResponse deactivateTag(@NotNull Long tagId, RbacPrincipal principal) {
        RecipeService.requirePlatformAdmin(principal);
        NutritionHealthTagPo tag = getTag(tagId);
        tag.setStatus(NutritionStatus.DISABLED);
        return toResponse(healthTagRepository.save(tag));
    }

    private List<HealthTagResponse> findTags(String tagType, boolean activeOnly) {
        List<NutritionHealthTagPo> tags;
        if (StringUtils.hasText(tagType)) {
            tags = activeOnly
                    ? healthTagRepository.findByTagTypeIgnoreCaseAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
                    normalizeCode(tagType), NutritionStatus.ACTIVE)
                    : healthTagRepository.findByTagTypeIgnoreCaseAndDeletedFalseOrderBySortOrderAscIdAsc(
                    normalizeCode(tagType));
        } else {
            tags = activeOnly
                    ? healthTagRepository.findByStatusAndDeletedFalseOrderByTagTypeAscSortOrderAscIdAsc(
                    NutritionStatus.ACTIVE)
                    : healthTagRepository.findByDeletedFalseOrderByTagTypeAscSortOrderAscIdAsc();
        }
        return tags.stream().map(this::toResponse).toList();
    }

    private NutritionHealthTagPo getTag(Long tagId) {
        return healthTagRepository.findByIdAndDeletedFalse(tagId)
                .orElseThrow(() -> new NutritionException(
                        "NUTRITION_HEALTH_TAG_NOT_FOUND", "nutrition health tag not found"));
    }

    private void apply(NutritionHealthTagPo tag, UpsertHealthTagRequest request,
                       String tagType, String tagCode) {
        tag.setTagType(tagType);
        tag.setTagCode(tagCode);
        tag.setName(request.name().trim());
        tag.setDescription(trimToNull(request.description()));
        tag.setSortOrder(request.sortOrder());
        tag.setDeleted(false);
    }

    private HealthTagResponse toResponse(NutritionHealthTagPo tag) {
        return new HealthTagResponse(tag.getId(), tag.getTagType(), tag.getTagCode(), tag.getName(),
                tag.getDescription(), tag.getSortOrder(), tag.getStatus(), tag.getCreatedAt(), tag.getUpdatedAt());
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long requireActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new NutritionException("NUTRITION_FORBIDDEN", "Nutrition family access is required");
        }
        return actorId;
    }

    private NutritionException duplicate() {
        return new NutritionException(
                "NUTRITION_HEALTH_TAG_DUPLICATE", "nutrition health tag code already exists for tag type");
    }
}
