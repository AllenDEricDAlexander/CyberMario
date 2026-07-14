package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import top.egon.mario.nutrition.dto.request.UpsertBudgetRuleRequest;
import top.egon.mario.nutrition.dto.response.BudgetRuleResponse;
import top.egon.mario.nutrition.dto.response.BudgetSummaryResponse;
import top.egon.mario.nutrition.service.BudgetService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.LocalDate;
import java.util.List;

/**
 * Budget summary endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}")
@Validated
public class BudgetController extends ReactiveNutritionSupport {

    private final BudgetService budgetService;

    @GetMapping("/budget/weekly")
    public Mono<ApiResponse<BudgetSummaryResponse>> weeklyBudget(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.weeklyBudget(familyId, weekStart, actorId(principal)));
    }

    @GetMapping("/budget/monthly")
    public Mono<ApiResponse<BudgetSummaryResponse>> monthlyBudget(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.monthlyBudget(familyId, month, actorId(principal)));
    }

    @GetMapping("/budget-rules")
    public Mono<ApiResponse<List<BudgetRuleResponse>>> budgetRules(
            @PathVariable @Min(1) Long familyId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.listBudgetRules(familyId, actorId(principal)));
    }

    @PostMapping("/budget-rules")
    public Mono<ApiResponse<BudgetRuleResponse>> createBudgetRule(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody UpsertBudgetRuleRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.createBudgetRule(familyId, request, actorId(principal)));
    }

    @PutMapping("/budget-rules/{ruleId}")
    public Mono<ApiResponse<BudgetRuleResponse>> updateBudgetRule(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long ruleId,
            @Valid @RequestBody UpsertBudgetRuleRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> budgetService.updateBudgetRule(familyId, ruleId, request, actorId(principal)));
    }

    @DeleteMapping("/budget-rules/{ruleId}")
    public Mono<ApiResponse<Void>> deactivateBudgetRule(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long ruleId,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blockingVoid(() -> budgetService.deactivateBudgetRule(familyId, ruleId, actorId(principal)));
    }
}
