package top.egon.mario.nutrition.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.service.ai.NutritionAiService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Polling endpoint for asynchronous AI recommendation jobs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}/ai-recommendation-jobs")
@Validated
public class AiRecommendationJobController extends ReactiveNutritionSupport {

    private final NutritionAiService aiService;

    @GetMapping
    public Mono<ApiResponse<List<NutritionAiRecommendationJobResponse>>> jobs(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> aiService.listJobs(familyId, actorId(principal)));
    }

    @GetMapping("/{jobId}")
    public Mono<ApiResponse<NutritionAiRecommendationJobResponse>> job(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long jobId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> aiService.getJob(familyId, jobId, actorId(principal)));
    }
}
