package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.GenerateAiRecommendationRequest;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationResponse;
import top.egon.mario.nutrition.service.ai.NutritionAiService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * AI recommendation endpoints for family nutrition meal-plan drafts.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}/ai-recommendations")
@Validated
public class AiRecommendationController extends ReactiveNutritionSupport {

    private final NutritionAiService aiService;

    @PostMapping("/generate")
    public Mono<ApiResponse<NutritionAiRecommendationJobResponse>> generateRecommendation(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody GenerateAiRecommendationRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> aiService.generateManualRecommendation(
                familyId, request.plannedDate(), request.mealTypes(), actorId(principal)));
    }

    @GetMapping
    public Mono<ApiResponse<List<NutritionAiRecommendationResponse>>> recommendations(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> aiService.listRecommendations(familyId, actorId(principal)));
    }

    @GetMapping("/{recommendationId}")
    public Mono<ApiResponse<NutritionAiRecommendationResponse>> recommendation(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recommendationId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> aiService.getRecommendation(familyId, recommendationId, actorId(principal)));
    }
}
