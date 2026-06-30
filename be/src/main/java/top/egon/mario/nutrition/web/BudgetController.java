package top.egon.mario.nutrition.web;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.service.BudgetService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.LocalDate;

/**
 * Budget summary endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}/budget")
@Validated
public class BudgetController extends ReactiveNutritionSupport {

    private final BudgetService budgetService;

    @GetMapping("/weekly")
    public Mono<ApiResponse<BudgetSummaryResponse>> weeklyBudget(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.weeklyBudget(familyId, weekStart, actorId(principal)));
    }

    @GetMapping("/monthly")
    public Mono<ApiResponse<BudgetSummaryResponse>> monthlyBudget(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.monthlyBudget(familyId, month, actorId(principal)));
    }
}
