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
import top.egon.mario.nutrition.dto.response.NutritionHomeOverviewResponse;
import top.egon.mario.nutrition.service.NutritionHomeQueryService;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.LocalDate;

/**
 * Live family nutrition home overview endpoint.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nutrition/families/{familyId}")
@Validated
public class NutritionHomeController extends ReactiveNutritionSupport {

    private final NutritionHomeQueryService homeQueryService;

    @GetMapping("/overview")
    public Mono<ApiResponse<NutritionHomeOverviewResponse>> overview(
            @PathVariable @Min(1) Long familyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal RbacPrincipal principal) {
        return blocking(() -> homeQueryService.overview(familyId, date, actorId(principal)));
    }
}
