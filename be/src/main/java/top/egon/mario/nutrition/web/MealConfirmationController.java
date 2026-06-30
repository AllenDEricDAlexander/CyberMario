package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.MealConfirmationRequest;
import top.egon.mario.nutrition.dto.response.MealConfirmationResponse;
import top.egon.mario.nutrition.service.MealConfirmationService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Meal confirmation endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}")
@Validated
public class MealConfirmationController extends ReactiveNutritionSupport {

    private final MealConfirmationService confirmationService;

    @PostMapping("/meal-plans/{mealPlanId}/confirmations")
    public Mono<ApiResponse<MealConfirmationResponse>> confirmMeal(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long mealPlanId,
            @Valid @RequestBody MealConfirmationRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> confirmationService.confirmMeal(
                familyId, mealPlanId, request, actorId(principal)));
    }

    @PutMapping("/confirmations/{confirmationId}")
    public Mono<ApiResponse<MealConfirmationResponse>> updateConfirmation(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long confirmationId,
            @Valid @RequestBody MealConfirmationRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> confirmationService.updateConfirmation(
                familyId, confirmationId, request, actorId(principal)));
    }
}
