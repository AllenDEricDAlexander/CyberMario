package top.egon.mario.nutrition.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.common.api.ApiResponse;
import top.egon.mario.nutrition.dto.request.CreateExtraFoodRecordRequest;
import top.egon.mario.nutrition.dto.request.NutritionRecordAdjustmentRequest;
import top.egon.mario.nutrition.dto.response.NutritionDailyOverviewResponse;
import top.egon.mario.nutrition.dto.response.NutritionRecordResponse;
import top.egon.mario.nutrition.dto.response.NutritionReportResponse;
import top.egon.mario.nutrition.service.NutritionRecordService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.LocalDate;

/**
 * Nutrition record and basic report endpoints for the nutrition MVP.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}/nutrition-records")
@Validated
public class NutritionRecordController extends ReactiveNutritionSupport {

    private final NutritionRecordService recordService;

    @GetMapping("/daily")
    public Mono<ApiResponse<NutritionDailyOverviewResponse>> dailyOverview(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.dailyOverview(familyId, date, actorId(principal)));
    }

    @PostMapping("/{recordId}/adjustments")
    public Mono<ApiResponse<NutritionRecordResponse>> adjustRecord(
            @PathVariable @Min(1) Long familyId,
            @PathVariable @Min(1) Long recordId,
            @Valid @RequestBody NutritionRecordAdjustmentRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.adjustRecord(familyId, recordId, request, actorId(principal)));
    }

    @PostMapping("/extra-foods")
    public Mono<ApiResponse<NutritionRecordResponse>> createExtraFoodRecord(
            @PathVariable @Min(1) Long familyId,
            @Valid @RequestBody CreateExtraFoodRecordRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.createExtraFoodRecord(familyId, request, actorId(principal)));
    }

    @GetMapping("/reports/family-weekly")
    public Mono<ApiResponse<NutritionReportResponse>> familyWeeklyReport(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.familyWeeklyReport(familyId, weekStart, actorId(principal)));
    }

    @GetMapping("/reports/family-monthly")
    public Mono<ApiResponse<NutritionReportResponse>> familyMonthlyReport(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.familyMonthlyReport(familyId, month, actorId(principal)));
    }

    @PostMapping("/reports/family-weekly/generate")
    public Mono<ApiResponse<NutritionReportResponse>> generateFamilyWeeklyReport(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.generateFamilyWeeklyReport(
                familyId, weekStart, actorId(principal)));
    }

    @PostMapping("/reports/family-monthly/generate")
    public Mono<ApiResponse<NutritionReportResponse>> generateFamilyMonthlyReport(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> recordService.generateFamilyMonthlyReport(
                familyId, month, actorId(principal)));
    }
}
