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
import top.egon.mario.nutrition.dto.request.CreateNutritionImportJobRequest;
import top.egon.mario.nutrition.dto.response.NutritionImportJobResponse;
import top.egon.mario.nutrition.service.importer.NutritionImportService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Import job endpoints for JSON-backed nutrition CSV import.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/platform/import-jobs")
@Validated
public class NutritionImportController extends ReactiveNutritionSupport {

    private final NutritionImportService importService;

    @PostMapping
    public Mono<ApiResponse<NutritionImportJobResponse>> createImportJob(
            @Valid @RequestBody CreateNutritionImportJobRequest request,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> importService.createImportJob(request, principal));
    }

    @GetMapping("/{jobId}")
    public Mono<ApiResponse<NutritionImportJobResponse>> importJob(@PathVariable @Min(1) Long jobId,
                                                                   @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> importService.getImportJob(jobId, principal));
    }

    @PostMapping("/{jobId}/confirm")
    public Mono<ApiResponse<NutritionImportJobResponse>> confirmImportJob(@PathVariable @Min(1) Long jobId,
                                                                          @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> importService.confirmImportJob(jobId, principal));
    }
}
