package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
import top.egon.mario.nutrition.dto.request.AcknowledgeMealRiskRequest;
import top.egon.mario.nutrition.dto.request.CreateTodayMealPlanRequest;
import top.egon.mario.nutrition.dto.request.UpdateMealPlanRequest;
import top.egon.mario.nutrition.dto.response.MealPlanResponse;
import top.egon.mario.nutrition.dto.response.MealPlanSummaryResponse;
import top.egon.mario.nutrition.dto.response.NutritionAiRecommendationJobResponse;
import top.egon.mario.nutrition.service.MealPlanService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

/**
 * Meal plan review and summary endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}/meal-plans")
@Validated
public class MealPlanController extends ReactiveNutritionSupport {

    private final MealPlanService mealPlanService;

    @GetMapping
    public Mono<ApiResponse<List<MealPlanResponse>>> mealPlans(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.listMealPlans(familyId, actorId(principal)));
    }

    @GetMapping("/today")
    public Mono<ApiResponse<List<MealPlanResponse>>> todayMealPlans(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.listTodayMealPlans(familyId, actorId(principal)));
    }

    @PostMapping("/today")
    public Mono<ApiResponse<MealPlanResponse>> createTodayMealPlan(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody CreateTodayMealPlanRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.createTodayMealPlan(familyId, request, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/publish")
    public Mono<ApiResponse<MealPlanResponse>> publishMealPlan(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.publishMealPlan(familyId, mealPlanId, actorId(principal)));
    }

    @PutMapping("/{mealPlanId}")
    public Mono<ApiResponse<MealPlanResponse>> updateMealPlan(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @Valid @RequestBody UpdateMealPlanRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.updateMealPlan(
                familyId, mealPlanId, request, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/risks/acknowledge")
    public Mono<ApiResponse<MealPlanResponse>> acknowledgeRisks(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @Valid @RequestBody AcknowledgeMealRiskRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.acknowledgeRisks(
                familyId, mealPlanId, request, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/regenerate")
    public Mono<ApiResponse<NutritionAiRecommendationJobResponse>> regenerateMealPlan(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.regenerateMealPlan(
                familyId, mealPlanId, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/close-confirmation")
    public Mono<ApiResponse<MealPlanResponse>> closeConfirmation(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @RequestParam(defaultValue = "false") boolean closeEarly,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.closeConfirmation(
                familyId, mealPlanId, closeEarly, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/start-preparing")
    public Mono<ApiResponse<MealPlanResponse>> startPreparing(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.startPreparing(familyId, mealPlanId, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/cancel")
    public Mono<ApiResponse<MealPlanResponse>> cancelMealPlan(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.cancelMealPlan(familyId, mealPlanId, actorId(principal)));
    }

    @PostMapping("/{mealPlanId}/complete")
    public Mono<ApiResponse<MealPlanResponse>> completeMealPlan(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.completeMealPlan(familyId, mealPlanId, actorId(principal)));
    }

    @GetMapping("/{mealPlanId}/summary")
    public Mono<ApiResponse<MealPlanSummaryResponse>> summary(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> mealPlanService.summary(familyId, mealPlanId, actorId(principal)));
    }
}
