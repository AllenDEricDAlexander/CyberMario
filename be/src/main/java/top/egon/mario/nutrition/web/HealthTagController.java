package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.UpsertHealthTagRequest;
import top.egon.mario.nutrition.dto.response.HealthTagResponse;
import top.egon.mario.nutrition.service.HealthTagService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Platform health-tag administration and family dictionary query endpoints.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition")
@Validated
public class HealthTagController extends ReactiveNutritionSupport {

    private final HealthTagService healthTagService;

    @GetMapping("/platform/health-tags")
    public Mono<ApiResponse<List<HealthTagResponse>>> platformTags(
            @RequestParam(required = false) String tagType,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> healthTagService.listTags(tagType, activeOnly, principal));
    }

    @PostMapping("/platform/health-tags")
    public Mono<ApiResponse<HealthTagResponse>> createTag(
            @Valid @RequestBody UpsertHealthTagRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> healthTagService.createTag(request, principal));
    }

    @PutMapping("/platform/health-tags/{tagId}")
    public Mono<ApiResponse<HealthTagResponse>> updateTag(
            @PathVariable @Min(1) Long tagId,
            @Valid @RequestBody UpsertHealthTagRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> healthTagService.updateTag(tagId, request, principal));
    }

    @DeleteMapping("/platform/health-tags/{tagId}")
    public Mono<ApiResponse<HealthTagResponse>> deactivateTag(
            @PathVariable @Min(1) Long tagId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> healthTagService.deactivateTag(tagId, principal));
    }

    @GetMapping("/families/{familyId}/health-tags")
    public Mono<ApiResponse<List<HealthTagResponse>>> familyTags(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) String tagType,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> healthTagService.listFamilyTags(familyId, tagType, actorId(principal)));
    }
}
